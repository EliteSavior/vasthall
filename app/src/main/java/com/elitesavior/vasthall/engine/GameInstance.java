package com.elitesavior.vasthall.engine;

import java.io.File;
import java.util.List;

/**
 * Long-lived game singleton. Unreal mental model: {@code UGameInstance} —
 * owns the {@link World}, {@link AssetRegistry}, {@link DeveloperConsole},
 * that world's {@link TimerManager}, {@link EventDispatcher},
 * {@link AudioManager}, {@link WidgetViewport}, {@link CollisionWorld},
 * and {@link SaveGameSystem} across level travel.
 * {@link GameMode} is created per {@link #openLevel}.
 *
 * <p>Startup flow: {@code Init} → {@link #init()} → {@link #openLevel(String)}
 * → {@link GameMode#initGame(String)} / {@link GameMode#startPlay()}.
 *
 * <p>Single-threaded: call from the same thread that ticks the world
 * (the activity frame callback).
 */
public final class GameInstance {
    private final AssetRegistry assets;
    private final World world;
    private final DeveloperConsole console;
    private final SaveGameSystem saves = new SaveGameSystem();
    private Class<? extends GameMode> defaultGameModeClass = HallGameMode.class;
    private GameMode gameMode;
    private boolean initialized;

    public GameInstance() {
        this(new AssetRegistry());
    }

    public GameInstance(AssetRegistry assets) {
        this.assets = assets == null ? new AssetRegistry() : assets;
        this.world = new World(this.assets);
        this.world.bindGameInstance(this);
        this.console = DeveloperConsole.withBuiltins(this.world);
    }

    /** Demo Hall catalog plus a console bound to this instance's world. */
    public static GameInstance withDemoAssets() {
        return new GameInstance(AssetRegistry.withDemoAssets());
    }

    public void init() {
        initialized = true;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public World world() {
        return world;
    }

    public AssetRegistry assets() {
        return assets;
    }

    public DeveloperConsole console() {
        return console;
    }

    public TimerManager timerManager() {
        return world.timerManager();
    }

    public EventDispatcher events() {
        return world.events();
    }

    public AudioManager audio() {
        return world.audio();
    }

    public WidgetViewport viewport() {
        return world.viewport();
    }

    public CollisionWorld collision() {
        return world.collision();
    }

    public SaveGameSystem saves() {
        return saves;
    }

    /** Tests and the activity inject the slot directory (no device default). */
    public void setSaveDirectory(File directory) {
        saves.setDirectory(directory);
    }

    public File saveDirectory() {
        return saves.directory();
    }

    /** {@code CreateSaveGameObject} — snapshot the live world. */
    public SaveGame createSaveGame() {
        return SaveGame.capture(world);
    }

    /** Capture the world and write {@code <slot>.sav}. Overwrites an existing slot. */
    public boolean saveGameToSlot(String slot) {
        return saves.saveToSlot(slot, createSaveGame());
    }

    public boolean saveGameToSlot(SaveGame save, String slot) {
        return saves.saveToSlot(slot, save);
    }

    /** Read the slot without applying it. Null if the slot is missing or invalid. */
    public SaveGame loadSaveGameObject(String slot) {
        return saves.loadFromSlot(slot);
    }

    /**
     * {@code LoadGameFromSlot}: read, {@code openLevel} the saved map, restore
     * matching actor fields. False if the slot does not exist.
     */
    public boolean loadGameFromSlot(String slot) {
        SaveGame save = saves.loadFromSlot(slot);
        if (save == null) {
            return false;
        }
        return save.apply(this);
    }

    public boolean doesSaveGameExist(String slot) {
        return saves.doesSlotExist(slot);
    }

    public boolean deleteGameInSlot(String slot) {
        return saves.deleteSlot(slot);
    }

    public List<String> saveSlots() {
        return saves.slots();
    }

    public GameMode gameMode() {
        return gameMode;
    }

    public void setDefaultGameModeClass(Class<? extends GameMode> type) {
        this.defaultGameModeClass = type == null ? GameMode.class : type;
    }

    public Class<? extends GameMode> defaultGameModeClass() {
        return defaultGameModeClass;
    }

    /**
     * Same-world OpenLevel through this instance: unload loaded streaming
     * levels, load {@code name}, then install that map's {@link GameMode}.
     */
    public Level openLevel(String name) {
        return openLevel(name, "");
    }

    public Level openLevel(String name, String options) {
        ensureInit();
        LevelDefinition definition = assets.findLevel(name);
        if (definition == null) {
            throw new IllegalArgumentException("unknown level: " + name);
        }
        Class<? extends GameMode> modeClass = resolveGameModeClass(name);
        teardownGameMode();
        Level level = world.openLevel(name);
        installGameMode(modeClass, options);
        return level;
    }

    /** Stream-load; does not change the current {@link GameMode}. */
    public Level loadLevel(String name) {
        ensureInit();
        return world.loadLevel(name);
    }

    /**
     * Unload a streaming level. When no levels remain, the current
     * {@link GameMode} is ended.
     */
    public boolean unloadLevel(String name) {
        ensureInit();
        boolean unloaded = world.unloadLevel(name);
        if (unloaded && world.loadedLevels().isEmpty()) {
            teardownGameMode();
        }
        return unloaded;
    }

    public void shutdown() {
        teardownGameMode();
        world.destroyAll();
        initialized = false;
    }

    private void ensureInit() {
        if (!initialized) {
            init();
        }
    }

    private Class<? extends GameMode> resolveGameModeClass(String name) {
        LevelDefinition definition = assets.findLevel(name);
        if (definition != null && definition.gameModeClassName() != null) {
            return GameModeTypes.resolve(definition.gameModeClassName());
        }
        return defaultGameModeClass;
    }

    private void installGameMode(Class<? extends GameMode> type, String options) {
        GameMode mode;
        try {
            mode = type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException failed) {
            throw new IllegalArgumentException(
                    "GameMode type needs a public no-arg constructor: " + type.getName(),
                    failed);
        }
        mode.bind(this, world);
        world.setGameMode(mode);
        gameMode = mode;
        mode.initGame(options);
        mode.startPlay();
    }

    private void teardownGameMode() {
        GameMode previous = gameMode;
        gameMode = null;
        world.setGameMode(null);
        if (previous != null) {
            previous.endPlay();
        }
    }
}
