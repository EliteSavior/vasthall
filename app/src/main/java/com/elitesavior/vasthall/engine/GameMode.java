package com.elitesavior.vasthall.engine;

import java.util.List;

/**
 * Per-level / play-session rules. Unreal mental model: {@code AGameModeBase}
 * / {@code AGameMode} — selected when a map opens, torn down on travel.
 *
 * <p>Hooks are thin: {@link #initGame(String)}, {@link #startPlay()},
 * {@link #endPlay()}, and a {@link #defaultPawnClass()} used to spawn a
 * pawn if the loaded level did not already place one.
 */
public class GameMode {
    private GameInstance game;
    private World world;
    private String options = "";
    private boolean started;
    private int initCount;
    private int startCount;
    private int endCount;

    public Class<? extends Actor> defaultPawnClass() {
        return PlayerPawn.class;
    }

    /** Unreal {@code InitGame} — called after the map is streamed in. */
    public void initGame(String options) {
        this.options = options == null ? "" : options;
        initCount++;
    }

    /**
     * Unreal {@code StartPlay} — session is live. Spawns
     * {@link #defaultPawnClass()} when the world has none.
     */
    public void startPlay() {
        started = true;
        startCount++;
        ensureDefaultPawn();
    }

    /** Called when this mode is replaced or the last level unloads. */
    public void endPlay() {
        started = false;
        endCount++;
    }

    public boolean hasStarted() {
        return started;
    }

    public String options() {
        return options;
    }

    public GameInstance gameInstance() {
        return game;
    }

    public World world() {
        return world;
    }

    public TimerManager timerManager() {
        return world == null ? null : world.timerManager();
    }

    public EventDispatcher events() {
        return world == null ? null : world.events();
    }

    public AudioManager audio() {
        return world == null ? null : world.audio();
    }

    public int initCount() {
        return initCount;
    }

    public int startCount() {
        return startCount;
    }

    public int endCount() {
        return endCount;
    }

    public Actor findDefaultPawn() {
        if (world == null) {
            return null;
        }
        Class<? extends Actor> type = defaultPawnClass();
        if (type == null) {
            return null;
        }
        List<? extends Actor> existing = world.actorsOf(type);
        return existing.isEmpty() ? null : existing.get(0);
    }

    protected Actor ensureDefaultPawn() {
        Actor existing = findDefaultPawn();
        if (existing != null || world == null) {
            return existing;
        }
        Class<? extends Actor> type = defaultPawnClass();
        if (type == null) {
            return null;
        }
        Actor pawn = world.spawnActor(type, Transform.identity());
        List<Level> levels = world.loadedLevels();
        if (levels.size() == 1) {
            world.bindActorToLoadedLevel(pawn, levels.get(0).name());
        }
        return pawn;
    }

    void bind(GameInstance game, World world) {
        this.game = game;
        this.world = world;
    }
}
