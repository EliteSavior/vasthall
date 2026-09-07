package com.elitesavior.vasthall.engine;

import java.util.function.Consumer;

/**
 * Unreal-style helpers over {@link World} / {@link GameInstance} level
 * streaming.
 *
 * <p>{@code LoadStreamLevel} → {@link #loadLevel}; {@code UnloadStreamLevel}
 * → {@link #unloadLevel}; {@code OpenLevel} → {@link #openLevel} (same-world
 * travel: unload loaded streaming levels, then load the named map). When the
 * world is owned by a {@link GameInstance}, open/unload go through it so
 * {@link GameMode} is installed or torn down. {@link #findAsset} /
 * {@link #loadAsset} look up the world's {@link AssetRegistry}.
 * {@link #getTimerManager} / {@link #setTimer} reach the world's
 * {@link TimerManager}. {@link #getEventDispatcher} / {@link #bindEvent}
 * reach the world's {@link EventDispatcher}. {@link #createSaveGame} /
 * {@link #saveGameToSlot} / {@link #loadGameFromSlot} /
 * {@link #doesSaveGameExist} / {@link #deleteGameInSlot} persist a
 * {@link SaveGame} through the owning {@link GameInstance}.
 */
public final class GameplayStatics {
    private GameplayStatics() {
    }

    public static Level loadLevel(World world, String name) {
        GameInstance game = requireWorld(world).gameInstance();
        if (game != null) {
            return game.loadLevel(name);
        }
        return world.loadLevel(name);
    }

    public static boolean unloadLevel(World world, String name) {
        GameInstance game = requireWorld(world).gameInstance();
        if (game != null) {
            return game.unloadLevel(name);
        }
        return world.unloadLevel(name);
    }

    public static Level openLevel(World world, String name) {
        GameInstance game = requireWorld(world).gameInstance();
        if (game != null) {
            return game.openLevel(name);
        }
        return world.openLevel(name);
    }

    public static Level openLevel(GameInstance game, String name) {
        if (game == null) {
            throw new IllegalArgumentException("game");
        }
        return game.openLevel(name);
    }

    public static GameInstance getGameInstance(World world) {
        return requireWorld(world).gameInstance();
    }

    public static GameMode getGameMode(World world) {
        return requireWorld(world).gameMode();
    }

    public static TimerManager getTimerManager(World world) {
        return requireWorld(world).timerManager();
    }

    public static TimerHandle setTimer(
            World world, Runnable callback, float rateSeconds, boolean looping) {
        return getTimerManager(world).setTimer(callback, rateSeconds, looping);
    }

    public static void clearTimer(World world, TimerHandle handle) {
        getTimerManager(world).clearTimer(handle);
    }

    public static EventDispatcher getEventDispatcher(World world) {
        return requireWorld(world).events();
    }

    public static <T> DelegateHandle bindEvent(
            World world, EventType<T> type, Consumer<T> listener) {
        return getEventDispatcher(world).bind(type, listener);
    }

    public static void unbindEvent(World world, DelegateHandle handle) {
        getEventDispatcher(world).unbind(handle);
    }

    /** Soft lookup. Missing names return null. */
    public static Asset findAsset(World world, String idOrPath) {
        return requireWorld(world).assets().find(idOrPath);
    }

    /** Hard lookup. Missing names throw {@code unknown asset}. */
    public static Asset loadAsset(World world, String idOrPath) {
        return requireWorld(world).assets().require(idOrPath);
    }

    public static SaveGame createSaveGame(GameInstance game) {
        return requireGame(game).createSaveGame();
    }

    public static boolean saveGameToSlot(GameInstance game, String slot) {
        return requireGame(game).saveGameToSlot(slot);
    }

    public static boolean saveGameToSlot(World world, String slot) {
        return saveGameToSlot(requireGame(getGameInstance(world)), slot);
    }

    public static boolean loadGameFromSlot(GameInstance game, String slot) {
        return requireGame(game).loadGameFromSlot(slot);
    }

    public static boolean loadGameFromSlot(World world, String slot) {
        return loadGameFromSlot(requireGame(getGameInstance(world)), slot);
    }

    public static boolean doesSaveGameExist(GameInstance game, String slot) {
        return requireGame(game).doesSaveGameExist(slot);
    }

    public static boolean doesSaveGameExist(World world, String slot) {
        return doesSaveGameExist(requireGame(getGameInstance(world)), slot);
    }

    public static boolean deleteGameInSlot(GameInstance game, String slot) {
        return requireGame(game).deleteGameInSlot(slot);
    }

    public static boolean deleteGameInSlot(World world, String slot) {
        return deleteGameInSlot(requireGame(getGameInstance(world)), slot);
    }

    private static GameInstance requireGame(GameInstance game) {
        if (game == null) {
            throw new IllegalArgumentException("game");
        }
        return game;
    }

    private static World requireWorld(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world");
        }
        return world;
    }
}
