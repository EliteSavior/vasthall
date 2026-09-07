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
        this.world = world;
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
        log.add("> " + trimmed + "\n" + output);
        while (log.size() > LOG_CAP) {
            log.remove(0);
        }
        return output;
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
        register("stat", "World actor/level/asset/frame/mode counts", this::statCommand);
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
        return "actors=" + live.actorCount()
                + " levels=" + live.loadedLevels().size()
                + " assets=" + live.assets().size()
                + " frame=" + live.frameCount()
                + " mode=" + mode;
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
