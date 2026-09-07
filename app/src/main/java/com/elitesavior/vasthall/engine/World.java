package com.elitesavior.vasthall.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Owns {@link Actor}s and ticks them. Unreal mental model: {@code UWorld}.
 *
 * <p>A {@link GameInstance} may own this world for the life of the play
 * session. Named {@link Level}s stream into this world via
 * {@link #loadLevel(String)} / {@link #unloadLevel(String)}.
 * {@link #openLevel(String)} is same-world OpenLevel: unload loaded
 * streaming levels, then load the named map. Prefer
 * {@link GameInstance#openLevel(String)} so a {@link GameMode} is installed.
 * Level definitions are resolved through {@link #assets()}.
 * {@link #timerManager()} is the world's {@code FTimerManager}.
 * {@link #events()} is the world's multicast event bus.
 * {@link #audio()} is the world's {@code UAudioDevice}-lite mixer.
 *
 * <p>Single-threaded: call spawn / destroy / tick / load from the same thread
 * (the activity frame callback).
 */
public final class World {
    private final List<Actor> living = new ArrayList<>();
    private final List<Actor> pendingAdd = new ArrayList<>();
    private final List<Actor> pendingKill = new ArrayList<>();
    private final List<LevelEvent> pendingLevelLoaded = new ArrayList<>();
    private final List<LevelEvent> pendingLevelUnloaded = new ArrayList<>();
    private final AssetRegistry assets;
    private final TimerManager timers = new TimerManager();
    private final EventDispatcher events = new EventDispatcher();
    private final AudioManager audio;
    private final Map<String, Level> loaded = new LinkedHashMap<>();
    private GameInstance gameInstance;
    private GameMode gameMode;
    private long nextId = 1L;
    private boolean ticking;
    private int frameCount;

    public World() {
        this(new AssetRegistry());
    }

    public World(AssetRegistry assets) {
        this.assets = assets == null ? new AssetRegistry() : assets;
        this.audio = new AudioManager(this.assets);
    }

    public AssetRegistry assets() {
        return assets;
    }

    public GameInstance gameInstance() {
        return gameInstance;
    }

    public GameMode gameMode() {
        return gameMode;
    }

    public TimerManager timerManager() {
        return timers;
    }

    public EventDispatcher events() {
        return events;
    }

    public AudioManager audio() {
        return audio;
    }

    void bindGameInstance(GameInstance gameInstance) {
        this.gameInstance = gameInstance;
    }

    void setGameMode(GameMode gameMode) {
        this.gameMode = gameMode;
    }

    public <T extends Actor> T spawnActor(Class<T> type) {
        return spawnActor(type, Transform.identity());
    }

    public <T extends Actor> T spawnActor(Class<T> type, Transform transform) {
        T actor;
        try {
            actor = type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException failed) {
            throw new IllegalArgumentException(
                    "Actor type needs a public no-arg constructor: " + type.getName(),
                    failed);
        }
        return spawnActor(actor, transform);
    }

    public void registerLevel(LevelDefinition definition) {
        assets.registerLevel(definition);
    }

    /**
     * Stream {@code name} into this world (Unreal {@code LoadStreamLevel}).
     * {@code name} may be the short id or the registered path. Already-loaded
     * names return the existing {@link Level} without spawning duplicates.
     */
    public Level loadLevel(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("level name");
        }
        LevelDefinition definition = assets.findLevel(name);
        if (definition == null) {
            throw new IllegalArgumentException("unknown level: " + name);
        }
        Level existing = loaded.get(definition.name());
        if (existing != null) {
            return existing;
        }
        Level level = new Level(definition.name());
        loaded.put(definition.name(), level);
        for (ActorTemplate template : definition.actors()) {
            Actor actor = spawnFromTemplate(template);
            actor.bindLevel(level.name());
            level.add(actor);
        }
        announceLevelLoaded(definition.name());
        return level;
    }

    /**
     * Remove {@code name} and destroy its actors (Unreal {@code UnloadStreamLevel}).
     * {@code name} may be the short id or the registered path.
     * Actors spawned outside this level stay in the world.
     */
    public boolean unloadLevel(String name) {
        Level level = findLoadedLevel(name);
        if (level == null) {
            return false;
        }
        name = level.name();
        List<Actor> owned = new ArrayList<>(level.actors());
        for (Actor actor : owned) {
            destroyActor(actor);
        }
        if (!ticking) {
            flushPending();
        }
        level.clear();
        loaded.remove(name);
        announceLevelUnloaded(name);
        return true;
    }

    /**
     * Same-world OpenLevel: unload every loaded streaming level, then load
     * {@code name}. Persistent (unleveled) actors are kept.
     */
    public Level openLevel(String name) {
        List<String> names = new ArrayList<>(loaded.keySet());
        for (String loadedName : names) {
            unloadLevel(loadedName);
        }
        return loadLevel(name);
    }

    public boolean isLevelLoaded(String name) {
        return findLoadedLevel(name) != null;
    }

    public Level findLoadedLevel(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Level direct = loaded.get(name);
        if (direct != null) {
            return direct;
        }
        LevelDefinition definition = assets.findLevel(name);
        if (definition == null) {
            return null;
        }
        return loaded.get(definition.name());
    }

    public List<Level> loadedLevels() {
        return new ArrayList<>(loaded.values());
    }

    /**
     * Bind a live actor to a loaded streaming level so unload / OpenLevel
     * destroy it with that map. No-op if the level is not loaded.
     */
    void bindActorToLoadedLevel(Actor actor, String levelName) {
        if (actor == null || actor.world() != this) {
            return;
        }
        Level level = findLoadedLevel(levelName);
        if (level == null) {
            return;
        }
        String previous = actor.levelName();
        if (previous != null && !previous.equals(level.name())) {
            Level old = loaded.get(previous);
            if (old != null) {
                old.remove(actor);
            }
        }
        actor.bindLevel(level.name());
        if (!level.contains(actor)) {
            level.add(actor);
        }
    }

    private Actor spawnFromTemplate(ActorTemplate template) {
        Class<? extends Actor> type = ActorTypes.resolve(template.className());
        Actor actor;
        try {
            actor = type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException failed) {
            throw new IllegalArgumentException(
                    "Actor type needs a public no-arg constructor: " + type.getName(),
                    failed);
        }
        if (template.name() != null) {
            actor.setName(template.name());
        }
        if (template.tickEnabled() != null) {
            actor.setActorTickEnabled(template.tickEnabled());
        }
        return spawnActor(actor, template.transform());
    }

    public <T extends Actor> T spawnActor(T actor, Transform transform) {
        if (actor == null) {
            throw new IllegalArgumentException("actor");
        }
        if (actor.world() != null) {
            throw new IllegalStateException("actor already belongs to a World");
        }
        Transform spawn = transform == null ? Transform.identity() : transform.copy();
        actor.attach(this, nextId++, spawn);
        if (ticking) {
            pendingAdd.add(actor);
        } else {
            living.add(actor);
            actor.callBeginPlay();
            broadcastActorSpawned(actor);
        }
        return actor;
    }

    public boolean destroyActor(Actor actor) {
        if (actor == null || actor.isPendingKill() || actor.world() != this) {
            return false;
        }
        actor.markPendingKill();
        pendingKill.add(actor);
        if (!ticking) {
            flushPending();
        }
        return true;
    }

    public void destroyAll() {
        List<String> names = new ArrayList<>(loaded.keySet());
        for (String loadedName : names) {
            unloadLevel(loadedName);
        }
        List<Actor> snapshot = new ArrayList<>(living.size() + pendingAdd.size());
        snapshot.addAll(living);
        snapshot.addAll(pendingAdd);
        for (Actor actor : snapshot) {
            destroyActor(actor);
        }
        if (ticking) {
            return;
        }
        flushPending();
        timers.clearAll();
        audio.stopAll();
    }

    public void tick(float deltaSeconds) {
        ticking = true;
        try {
            timers.tick(deltaSeconds);
            for (int i = 0; i < living.size(); i++) {
                Actor actor = living.get(i);
                if (!actor.isPendingKill() && actor.isActorTickEnabled()) {
                    actor.callTick(deltaSeconds);
                }
            }
        } finally {
            ticking = false;
            flushPending();
            frameCount++;
        }
    }

    public int actorCount() {
        int count = 0;
        for (Actor actor : living) {
            if (!actor.isPendingKill()) {
                count++;
            }
        }
        for (Actor actor : pendingAdd) {
            if (!actor.isPendingKill()) {
                count++;
            }
        }
        return count;
    }

    public int frameCount() {
        return frameCount;
    }

    /** Mutable copy of living actors. Clearing the list does not change the world. */
    public List<Actor> actors() {
        List<Actor> copy = new ArrayList<>(living.size());
        for (Actor actor : living) {
            if (!actor.isPendingKill()) {
                copy.add(actor);
            }
        }
        for (Actor actor : pendingAdd) {
            if (!actor.isPendingKill()) {
                copy.add(actor);
            }
        }
        return copy;
    }

    public Actor findActor(String name) {
        if (name == null) {
            return null;
        }
        for (Actor actor : actors()) {
            if (name.equals(actor.name())) {
                return actor;
            }
        }
        return null;
    }

    public <T extends Actor> List<T> actorsOf(Class<T> type) {
        List<T> matched = new ArrayList<>();
        for (Actor actor : actors()) {
            if (type.isInstance(actor)) {
                matched.add(type.cast(actor));
            }
        }
        return matched;
    }

    public void appendDump(StringBuilder out) {
        out.append("game.instance=").append(gameInstance == null ? 0 : 1).append('\n');
        out.append("game.saves=").append(gameInstance == null ? 0 : gameInstance.saveSlots().size())
                .append('\n');
        if (gameMode == null) {
            out.append("game.mode=-\n");
        } else {
            Class<? extends Actor> pawn = gameMode.defaultPawnClass();
            out.append("game.mode=").append(gameMode.getClass().getSimpleName())
                    .append(" pawn=").append(pawn == null ? "-" : pawn.getSimpleName())
                    .append(" started=").append(gameMode.hasStarted() ? 1 : 0)
                    .append('\n');
        }
        out.append("world.actors=").append(actorCount()).append('\n');
        out.append("world.frame=").append(frameCount).append('\n');
        out.append("world.levels=").append(loaded.size()).append('\n');
        timers.appendDump(out);
        events.appendDump(out);
        audio.appendDump(out);
        assets.appendDump(out);
        for (Level level : loaded.values()) {
            out.append("level=").append(level.name())
                    .append(" actors=").append(level.actorCount())
                    .append('\n');
        }
        for (Actor actor : actors()) {
            Transform t = actor.transform();
            String levelName = actor.levelName();
            out.append("actor id=").append(actor.id())
                    .append(" name=").append(actor.name())
                    .append(" class=").append(actor.getClass().getSimpleName())
                    .append(" level=").append(levelName == null ? "-" : levelName)
                    .append(" tick=").append(actor.isActorTickEnabled() ? 1 : 0)
                    .append(" loc=").append(fmt(t.location.x)).append(',')
                    .append(fmt(t.location.y)).append(',').append(fmt(t.location.z))
                    .append(" rot=").append(fmt(t.rotation.pitch)).append(',')
                    .append(fmt(t.rotation.yaw)).append(',').append(fmt(t.rotation.roll))
                    .append(" scale=").append(fmt(t.scale.x)).append(',')
                    .append(fmt(t.scale.y)).append(',').append(fmt(t.scale.z))
                    .append(" components=").append(actor.componentCount())
                    .append('\n');
            actor.appendComponentDump(out);
        }
    }

    private void flushPending() {
        if (!pendingKill.isEmpty()) {
            for (Actor actor : pendingKill) {
                forgetFromLevel(actor);
                living.remove(actor);
                pendingAdd.remove(actor);
                actor.callEndPlay();
                broadcastActorDestroyed(actor);
                actor.detach();
            }
            pendingKill.clear();
        }
        if (!pendingAdd.isEmpty()) {
            List<Actor> added = new ArrayList<>(pendingAdd);
            pendingAdd.clear();
            for (Actor actor : added) {
                if (actor.isPendingKill()) {
                    continue;
                }
                living.add(actor);
                actor.callBeginPlay();
                broadcastActorSpawned(actor);
            }
        }
        flushPendingLevelEvents();
    }

    private void announceLevelLoaded(String name) {
        LevelEvent event = new LevelEvent(this, name);
        if (ticking) {
            pendingLevelLoaded.add(event);
        } else {
            events.broadcast(EventType.LEVEL_LOADED, event);
        }
    }

    private void announceLevelUnloaded(String name) {
        LevelEvent event = new LevelEvent(this, name);
        if (ticking) {
            pendingLevelUnloaded.add(event);
        } else {
            events.broadcast(EventType.LEVEL_UNLOADED, event);
        }
    }

    private void flushPendingLevelEvents() {
        if (!pendingLevelUnloaded.isEmpty()) {
            List<LevelEvent> unloaded = new ArrayList<>(pendingLevelUnloaded);
            pendingLevelUnloaded.clear();
            for (LevelEvent event : unloaded) {
                events.broadcast(EventType.LEVEL_UNLOADED, event);
            }
        }
        if (!pendingLevelLoaded.isEmpty()) {
            List<LevelEvent> loadedEvents = new ArrayList<>(pendingLevelLoaded);
            pendingLevelLoaded.clear();
            for (LevelEvent event : loadedEvents) {
                events.broadcast(EventType.LEVEL_LOADED, event);
            }
        }
    }

    private void broadcastActorSpawned(Actor actor) {
        events.broadcast(EventType.ACTOR_SPAWNED, new ActorEvent(this, actor));
    }

    private void broadcastActorDestroyed(Actor actor) {
        events.broadcast(EventType.ACTOR_DESTROYED, new ActorEvent(this, actor));
    }

    private void forgetFromLevel(Actor actor) {
        String levelName = actor.levelName();
        if (levelName == null) {
            return;
        }
        Level level = loaded.get(levelName);
        if (level != null) {
            level.remove(actor);
        }
    }

    private static String fmt(float value) {
        return String.format(Locale.US, "%.4f", value);
    }
}
