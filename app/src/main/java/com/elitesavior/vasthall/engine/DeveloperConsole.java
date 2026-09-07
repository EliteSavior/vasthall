package com.elitesavior.vasthall.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * In-engine developer console. Unreal mental model: the {@code ~} console —
 * register named commands, type a line, get text back.
 *
 * <p>Single-threaded: call {@link #exec(String)} from the same thread that
 * owns the bound {@link World} (the activity frame / overlay).
 */
public final class DeveloperConsole {
    public interface Command {
        String run(World world, String[] args);
    }

    private static final class Entry {
        final String name;
        final String help;
        final Command command;

        Entry(String name, String help, Command command) {
            this.name = name;
            this.help = help;
            this.command = command;
        }
    }

    private World world;
    private final Map<String, Entry> commands = new LinkedHashMap<>();
    private final Map<Long, TimerHandle> scheduled = new LinkedHashMap<>();
    private final List<DelegateHandle> eventBinds = new ArrayList<>();
    private TimerHandle lastTimer;
    private static final int LOG_CAP = 80;

    private final List<String> log = new ArrayList<>();

    public DeveloperConsole() {
        this(null);
    }

    public DeveloperConsole(World world) {
        this.world = world;
    }

    public static DeveloperConsole withBuiltins(World world) {
        DeveloperConsole console = new DeveloperConsole(world);
        console.registerBuiltins();
        return console;
    }

    public World world() {
        return world;
    }

    public void setWorld(World world) {
        unbindEngineEvents();
        this.world = world;
        bindEngineEvents();
    }

    public void register(String name, String help, Command command) {
        String key = requireName(name);
        if (command == null) {
            throw new IllegalArgumentException("command");
        }
        if (commands.containsKey(key)) {
            throw new IllegalArgumentException("command already registered: " + key);
        }
        String text = help == null ? "" : help.trim();
        commands.put(key, new Entry(key, text, command));
    }

    public boolean isRegistered(String name) {
        return find(name) != null;
    }

    public List<String> commandNames() {
        return new ArrayList<>(commands.keySet());
    }

    public String helpText(String name) {
        Entry entry = find(name);
        return entry == null ? null : entry.help;
    }

    public String exec(String line) {
        if (line == null || line.trim().isEmpty()) {
            return "";
        }
        String trimmed = line.trim();
        String[] tokens = trimmed.split("\\s+");
        String name = tokens[0];
        Entry entry = find(name);
        String output;
        if (entry == null) {
            output = "unknown command: " + name + "\nType help for commands.";
        } else {
            String[] args = new String[tokens.length - 1];
            System.arraycopy(tokens, 1, args, 0, args.length);
            try {
                output = entry.command.run(world, args);
                if (output == null) {
                    output = "";
                }
            } catch (RuntimeException failed) {
                String message = failed.getMessage();
                output = "error: " + (message == null ? failed.getClass().getSimpleName() : message);
            }
        }
        appendLog("> " + trimmed + "\n" + output);
        return output;
    }

    /** Append a line (timer callbacks, notes). Same cap as {@link #exec(String)}. */
    public void appendLog(String line) {
        if (line == null || line.isEmpty()) {
            return;
        }
        log.add(line);
        while (log.size() > LOG_CAP) {
            log.remove(0);
        }
    }

    public List<String> log() {
        return new ArrayList<>(log);
    }

    public void clearLog() {
        log.clear();
    }

    public void registerBuiltins() {
        register("help", "List commands, or help <name>", this::helpCommand);
        register("actors", "List actors in the World", this::actorsCommand);
        register("listactors", "Alias for actors", this::actorsCommand);
        register("assets", "List registered assets", this::assetsCommand);
        register("listassets", "Alias for assets", this::assetsCommand);
        register("load", "LoadStreamLevel <name>", this::loadCommand);
        register("loadlevel", "Alias for load", this::loadCommand);
        register("unload", "UnloadStreamLevel <name>", this::unloadCommand);
        register("unloadlevel", "Alias for unload", this::unloadCommand);
        register("open", "OpenLevel <name> (same-world travel)", this::openCommand);
        register("openlevel", "Alias for open", this::openCommand);
        register("stat", "World actor/level/asset/frame/mode/timer/audio/widget/overlap/action counts", this::statCommand);
        register("settimer", "SetTimer <seconds> [once|loop] [message]", this::setTimerCommand);
        register("cleartimer", "ClearTimer [id] (last if omitted)", this::clearTimerCommand);
        register("timers", "List active TimerManager timers", this::timersCommand);
        register("events", "List EventDispatcher listener counts", this::eventsCommand);
        register("savegame", "SaveGame <slot>", this::saveGameCommand);
        register("loadgame", "LoadGame <slot>", this::loadGameCommand);
        register("playsound", "PlaySound <id>", this::playSoundCommand);
        register("stopsound", "StopSound <id>", this::stopSoundCommand);
        register("setmastervolume", "SetMasterVolume <0-1>", this::setMasterVolumeCommand);
        register("audio", "List playing AudioManager voices", this::audioCommand);
        register("createwidget", "CreateWidget <class> <name> [text]", this::createWidgetCommand);
        register("addtoviewport", "AddToViewport <name>", this::addToViewportCommand);
        register("hidewidget", "HideWidget <name>", this::hideWidgetCommand);
        register("showwidget", "ShowWidget <name>", this::showWidgetCommand);
        register("removefromparent", "RemoveFromParent <name>", this::removeFromParentCommand);
        register("widgets", "List WidgetViewport slots", this::widgetsCommand);
        register("listoverlaps", "List current CollisionWorld overlap pairs", this::listOverlapsCommand);
        register("overlaps", "Alias for listoverlaps", this::listOverlapsCommand);
        register("debugdrawoverlaps", "DebugDrawOverlaps <0|1> stub flag", this::debugDrawOverlapsCommand);
        register("input", "List InputAction mappings and values", this::inputCommand);
        bindEngineEvents();
    }

    private String helpCommand(World bound, String[] args) {
        if (args.length > 0) {
            Entry entry = find(args[0]);
            if (entry == null) {
                return "unknown command: " + args[0];
            }
            return entry.help;
        }
        StringBuilder out = new StringBuilder();
        out.append("commands:\n");
        for (Entry entry : commands.values()) {
            out.append(entry.name);
            if (!entry.help.isEmpty()) {
                out.append(" — ").append(entry.help);
            }
            out.append('\n');
        }
        return out.toString().trim();
    }

    private String actorsCommand(World bound, String[] args) {
        World live = requireWorld(bound);
        List<Actor> actors = live.actors();
        StringBuilder out = new StringBuilder();
        out.append("actors=").append(actors.size()).append('\n');
        for (Actor actor : actors) {
            Transform t = actor.transform();
            String levelName = actor.levelName();
            out.append(actor.name())
                    .append(" class=").append(actor.getClass().getSimpleName())
                    .append(" level=").append(levelName == null ? "-" : levelName)
                    .append(" loc=")
                    .append(fmt(t.location.x)).append(',')
                    .append(fmt(t.location.y)).append(',')
                    .append(fmt(t.location.z))
                    .append('\n');
        }
        return out.toString().trim();
    }

    private String assetsCommand(World bound, String[] args) {
        World live = requireWorld(bound);
        List<Asset> assets = live.assets().assets();
        StringBuilder out = new StringBuilder();
        out.append("assets=").append(assets.size()).append('\n');
        for (Asset asset : assets) {
            out.append("id=").append(asset.id())
                    .append(" path=").append(asset.path())
                    .append(" kind=").append(asset.kind().name())
                    .append('\n');
        }
        return out.toString().trim();
    }

    private String loadCommand(World bound, String[] args) {
        World live = requireWorld(bound);
        String name = requireLevelName(args);
        Level level = GameplayStatics.loadLevel(live, name);
        return "loaded " + level.name() + " actors=" + level.actorCount();
    }

    private String unloadCommand(World bound, String[] args) {
        World live = requireWorld(bound);
        String name = requireLevelName(args);
        if (!GameplayStatics.unloadLevel(live, name)) {
            return "not loaded: " + name;
        }
        return "unloaded " + name;
    }

    private String openCommand(World bound, String[] args) {
        World live = requireWorld(bound);
        String name = requireLevelName(args);
        Level level = GameplayStatics.openLevel(live, name);
        return "opened " + level.name() + " actors=" + level.actorCount();
    }

    private String statCommand(World bound, String[] args) {
        World live = requireWorld(bound);
        String mode = live.gameMode() == null
                ? "-"
                : live.gameMode().getClass().getSimpleName();
        int saves = live.gameInstance() == null ? 0 : live.gameInstance().saveSlots().size();
        return "actors=" + live.actorCount()
                + " levels=" + live.loadedLevels().size()
                + " assets=" + live.assets().size()
                + " frame=" + live.frameCount()
                + " mode=" + mode
                + " timers=" + live.timerManager().timerCount()
                + " events=" + live.events().listenerCount()
                + " saves=" + saves
                + " audio=" + live.audio().playingCount()
                + " widgets=" + live.viewport().viewportCount()
                + " overlaps=" + live.collision().overlapCount()
                + " actions=" + live.input().actionCount();
    }

    private String setTimerCommand(World bound, String[] args) {
        World live = requireWorld(bound);
        if (args.length == 0 || args[0] == null || args[0].isEmpty()) {
            throw new IllegalArgumentException("delay seconds");
        }
        float delay;
        try {
            delay = Float.parseFloat(args[0]);
        } catch (NumberFormatException failed) {
            throw new IllegalArgumentException("delay seconds");
        }
        boolean looping = false;
        int messageStart = 1;
        if (args.length > 1
                && ("loop".equalsIgnoreCase(args[1]) || "once".equalsIgnoreCase(args[1]))) {
            looping = "loop".equalsIgnoreCase(args[1]);
            messageStart = 2;
        }
        String message = messageStart < args.length ? joinArgs(args, messageStart) : "timer";
        final long[] idHolder = new long[1];
        final boolean loop = looping;
        TimerHandle handle = live.timerManager().setTimer(() -> {
            appendLog("timer fired id=" + idHolder[0] + " " + message);
            if (!loop) {
                scheduled.remove(idHolder[0]);
            }
        }, delay, looping);
        idHolder[0] = handle.id();
        scheduled.put(handle.id(), handle);
        lastTimer = handle;
        return "timer " + handle.id() + " set " + fmt(delay) + "s " + (looping ? "loop" : "once");
    }

    private String clearTimerCommand(World bound, String[] args) {
        World live = requireWorld(bound);
        TimerHandle handle;
        if (args.length == 0) {
            handle = lastTimer;
        } else {
            long id;
            try {
                id = Long.parseLong(args[0]);
            } catch (NumberFormatException failed) {
                throw new IllegalArgumentException("timer id");
            }
            handle = scheduled.get(id);
            if (handle == null) {
                handle = live.timerManager().findTimer(id);
            }
        }
        if (handle == null || !handle.isValid()) {
            return "no timer";
        }
        long id = handle.id();
        live.timerManager().clearTimer(handle);
        scheduled.remove(id);
        if (lastTimer == handle) {
            lastTimer = null;
        }
        return "cleared " + id;
    }

    private String saveGameCommand(World bound, String[] args) {
        GameInstance game = requireGame(bound);
        String slot = requireSlotName(args);
        if (!game.saveGameToSlot(slot)) {
            throw new IllegalStateException("save failed");
        }
        SaveGame save = game.loadSaveGameObject(slot);
        String level = save == null || save.levelName().isEmpty() ? "-" : save.levelName();
        int actors = save == null ? 0 : save.actors().size();
        return "saved " + slot + " level=" + level + " actors=" + actors;
    }

    private String loadGameCommand(World bound, String[] args) {
        GameInstance game = requireGame(bound);
        String slot = requireSlotName(args);
        if (!game.doesSaveGameExist(slot)) {
            throw new IllegalArgumentException("no save: " + slot);
        }
        if (!game.loadGameFromSlot(slot)) {
            throw new IllegalStateException("load failed");
        }
        String level = currentLevelName(game.world());
        return "loaded " + slot + " level=" + level + " actors=" + game.world().actorCount();
    }

    private String playSoundCommand(World bound, String[] args) {
        World live = requireWorld(bound);
        String id = requireSoundId(args);
        if (!live.audio().play(id)) {
            throw new IllegalArgumentException("unknown sound: " + id);
        }
        Asset asset = live.assets().find(id);
        String resolved = asset == null ? id : asset.id();
        return "playing " + resolved;
    }

    private String stopSoundCommand(World bound, String[] args) {
        World live = requireWorld(bound);
        String id = requireSoundId(args);
        if (!live.audio().stop(id)) {
            throw new IllegalArgumentException("not playing: " + id);
        }
        Asset asset = live.assets().find(id);
        String resolved = asset == null ? id : asset.id();
        return "stopped " + resolved;
    }

    private String setMasterVolumeCommand(World bound, String[] args) {
        World live = requireWorld(bound);
        if (args.length == 0 || args[0] == null || args[0].isEmpty()) {
            throw new IllegalArgumentException("volume");
        }
        float volume;
        try {
            volume = Float.parseFloat(args[0]);
        } catch (NumberFormatException failed) {
            throw new IllegalArgumentException("volume");
        }
        live.audio().setMasterVolume(volume);
        return "volume " + fmt(live.audio().masterVolume());
    }

    private String audioCommand(World bound, String[] args) {
        World live = requireWorld(bound);
        AudioManager audio = live.audio();
        StringBuilder out = new StringBuilder();
        out.append("audio=").append(audio.playingCount()).append('\n');
        for (String line : audio.describe()) {
            out.append(line).append('\n');
        }
        return out.toString().trim();
    }

    private String createWidgetCommand(World bound, String[] args) {
        World live = requireWorld(bound);
        if (args.length < 2 || args[0] == null || args[0].isEmpty()
                || args[1] == null || args[1].isEmpty()) {
            throw new IllegalArgumentException("widget class and name");
        }
        Widget widget = live.viewport().createWidget(args[0], args[1]);
        if (widget instanceof TextWidget && args.length > 2) {
            ((TextWidget) widget).setText(joinArgs(args, 2));
        }
        return "created " + widget.name()
                + " class=" + widget.getClass().getSimpleName();
    }

    private String addToViewportCommand(World bound, String[] args) {
        Widget widget = requireWidget(bound, args);
        if (!widget.addToViewport()) {
            throw new IllegalStateException("add failed: " + widget.name());
        }
        return "viewport " + widget.name();
    }

    private String hideWidgetCommand(World bound, String[] args) {
        Widget widget = requireWidget(bound, args);
        widget.hide();
        return "hidden " + widget.name();
    }

    private String showWidgetCommand(World bound, String[] args) {
        Widget widget = requireWidget(bound, args);
        widget.show();
        return "shown " + widget.name();
    }

    private String removeFromParentCommand(World bound, String[] args) {
        Widget widget = requireWidget(bound, args);
        if (!widget.removeFromParent()) {
            throw new IllegalArgumentException("not on viewport: " + widget.name());
        }
        return "removed " + widget.name();
    }

    private String widgetsCommand(World bound, String[] args) {
        World live = requireWorld(bound);
        WidgetViewport viewport = live.viewport();
        StringBuilder out = new StringBuilder();
        out.append("widgets=").append(viewport.viewportCount()).append('\n');
        for (String line : viewport.describe()) {
            out.append(line).append('\n');
        }
        return out.toString().trim();
    }

    private String listOverlapsCommand(World bound, String[] args) {
        World live = requireWorld(bound);
        CollisionWorld collision = live.collision();
        StringBuilder out = new StringBuilder();
        out.append("overlaps=").append(collision.overlapCount()).append('\n');
        for (String line : collision.describe()) {
            out.append(line).append('\n');
        }
        return out.toString().trim();
    }

    private String debugDrawOverlapsCommand(World bound, String[] args) {
        World live = requireWorld(bound);
        if (args.length == 0 || args[0] == null || args[0].isEmpty()) {
            throw new IllegalArgumentException("0 or 1");
        }
        String token = args[0];
        boolean enabled;
        if ("1".equals(token) || "on".equalsIgnoreCase(token) || "true".equalsIgnoreCase(token)) {
            enabled = true;
        } else if ("0".equals(token) || "off".equalsIgnoreCase(token) || "false".equalsIgnoreCase(token)) {
            enabled = false;
        } else {
            throw new IllegalArgumentException("0 or 1");
        }
        live.collision().setDebugDraw(enabled);
        return "debugDraw=" + (enabled ? 1 : 0);
    }

    private String inputCommand(World bound, String[] args) {
        World live = requireWorld(bound);
        InputSubsystem input = live.input();
        StringBuilder out = new StringBuilder();
        out.append("actions=").append(input.actionCount()).append('\n');
        for (String line : input.describe()) {
            out.append(line).append('\n');
        }
        return out.toString().trim();
    }

    private String eventsCommand(World bound, String[] args) {
        World live = requireWorld(bound);
        EventDispatcher dispatcher = live.events();
        StringBuilder out = new StringBuilder();
        out.append("events=").append(dispatcher.listenerCount()).append('\n');
        for (String line : dispatcher.describe()) {
            out.append(line).append('\n');
        }
        return out.toString().trim();
    }

    private void bindEngineEvents() {
        if (world == null) {
            return;
        }
        EventDispatcher dispatcher = world.events();
        eventBinds.add(dispatcher.bind(EventType.LEVEL_LOADED, this::onLevelLoaded));
        eventBinds.add(dispatcher.bind(EventType.LEVEL_UNLOADED, this::onLevelUnloaded));
        eventBinds.add(dispatcher.bind(EventType.ACTOR_SPAWNED, this::onActorSpawned));
        eventBinds.add(dispatcher.bind(EventType.ACTOR_DESTROYED, this::onActorDestroyed));
        eventBinds.add(dispatcher.bind(EventType.BEGIN_OVERLAP, this::onBeginOverlap));
        eventBinds.add(dispatcher.bind(EventType.END_OVERLAP, this::onEndOverlap));
    }

    private void unbindEngineEvents() {
        if (world == null) {
            eventBinds.clear();
            return;
        }
        EventDispatcher dispatcher = world.events();
        for (DelegateHandle handle : eventBinds) {
            dispatcher.unbind(handle);
        }
        eventBinds.clear();
    }

    private void onLevelLoaded(LevelEvent event) {
        appendLog("event LevelLoaded " + event.levelName());
    }

    private void onLevelUnloaded(LevelEvent event) {
        appendLog("event LevelUnloaded " + event.levelName());
    }

    private void onActorSpawned(ActorEvent event) {
        Actor actor = event.actor();
        appendLog("event ActorSpawned " + (actor == null ? "-" : actor.name()));
    }

    private void onActorDestroyed(ActorEvent event) {
        Actor actor = event.actor();
        appendLog("event ActorDestroyed " + (actor == null ? "-" : actor.name()));
    }

    private void onBeginOverlap(OverlapEvent event) {
        appendLog("event BeginOverlap " + overlapLabel(event));
    }

    private void onEndOverlap(OverlapEvent event) {
        appendLog("event EndOverlap " + overlapLabel(event));
    }

    private static String overlapLabel(OverlapEvent event) {
        String left = event.actor() == null ? "-" : event.actor().name();
        String right = event.otherActor() == null ? "-" : event.otherActor().name();
        return left + " vs " + right;
    }

    private String timersCommand(World bound, String[] args) {
        World live = requireWorld(bound);
        TimerManager manager = live.timerManager();
        StringBuilder out = new StringBuilder();
        out.append("timers=").append(manager.timerCount()).append('\n');
        for (String line : manager.describe()) {
            out.append(line).append('\n');
        }
        return out.toString().trim();
    }

    private static String joinArgs(String[] args, int start) {
        StringBuilder out = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) {
                out.append(' ');
            }
            out.append(args[i]);
        }
        return out.toString();
    }

    private Entry find(String name) {
        if (name == null) {
            return null;
        }
        return commands.get(name.trim().toLowerCase(Locale.US));
    }

    private static World requireWorld(World world) {
        if (world == null) {
            throw new IllegalArgumentException("no world");
        }
        return world;
    }

    private static GameInstance requireGame(World world) {
        World live = requireWorld(world);
        GameInstance game = live.gameInstance();
        if (game == null) {
            throw new IllegalArgumentException("no game instance");
        }
        return game;
    }

    private static Widget requireWidget(World world, String[] args) {
        World live = requireWorld(world);
        if (args == null || args.length == 0 || args[0] == null || args[0].isEmpty()) {
            throw new IllegalArgumentException("widget name");
        }
        Widget widget = live.viewport().find(args[0]);
        if (widget == null) {
            throw new IllegalArgumentException("unknown widget: " + args[0]);
        }
        return widget;
    }

    private static String requireSoundId(String[] args) {
        if (args == null || args.length == 0 || args[0] == null || args[0].isEmpty()) {
            throw new IllegalArgumentException("sound id");
        }
        return args[0];
    }

    private static String requireSlotName(String[] args) {
        if (args == null || args.length == 0 || args[0] == null || args[0].isEmpty()) {
            throw new IllegalArgumentException("slot name");
        }
        return args[0];
    }

    private static String currentLevelName(World world) {
        List<Level> levels = world.loadedLevels();
        return levels.isEmpty() ? "-" : levels.get(0).name();
    }

    private static String requireLevelName(String[] args) {
        if (args == null || args.length == 0 || args[0] == null || args[0].isEmpty()) {
            throw new IllegalArgumentException("level name");
        }
        return args[0];
    }

    private static String requireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("command name");
        }
        return name.trim().toLowerCase(Locale.US);
    }

    private static String fmt(float value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
