package com.elitesavior.vasthall.engine;

import java.util.List;
import java.util.function.Consumer;

/**
 * Unreal-style helpers over {@link World} / {@link GameInstance} level
 * streaming.
 *
 * <p>{@code LoadStreamLevel} → {@link #loadLevel}; {@code UnloadStreamLevel}
 * → {@link #unloadLevel}; {@code OpenLevel} → {@link #openLevel} (same-world
 * travel: unload loaded streaming levels, then load the named map). When the
 * world is owned by a {@link GameInstance}, open/unload go through it so
 * {@link GameMode} is installed or torn down. {@link #findAsset} / {@link #loadAsset} /
 * {@link #findDataAsset} / {@link #loadDataAsset} look up the world's
 * {@link AssetRegistry}.
 * {@link #getTimerManager} / {@link #setTimer} reach the world's
 * {@link TimerManager}. {@link #getEventDispatcher} / {@link #bindEvent}
 * reach the world's {@link EventDispatcher}. {@link #createSaveGame} /
 * {@link #saveGameToSlot} / {@link #loadGameFromSlot} /
 * {@link #doesSaveGameExist} / {@link #deleteGameInSlot} persist a
 * {@link SaveGame} through the owning {@link GameInstance}.
 * {@link #getAudioManager} / {@link #playSound2D} / {@link #stopSound}
 * / {@link #setMasterVolume} reach the world's {@link AudioManager}.
 * {@link #getWidgetViewport} / {@link #createWidget} /
 * {@link #addToViewport} / {@link #removeFromParent} /
 * {@link #showWidget} / {@link #hideWidget} reach the world's
 * {@link WidgetViewport}. {@link #getCollisionWorld} /
 * {@link #queryOverlaps} / {@link #isOverlapping} reach the world's
 * {@link CollisionWorld}. {@link #getInputSubsystem} /
 * {@link #getPlayerController} / {@link #bindAction} /
 * {@link #injectKey} / {@link #injectAxis} reach Enhanced Input–lite.
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

    /** Soft DataAsset lookup. Missing names or wrong type return null. */
    public static DataAsset findDataAsset(World world, String idOrPath) {
        return requireWorld(world).assets().findDataAsset(idOrPath);
    }

    /** Hard DataAsset lookup. Missing names or wrong type throw. */
    public static <T extends DataAsset> T loadDataAsset(
            World world, String idOrPath, Class<T> type) {
        return requireWorld(world).assets().requireDataAsset(idOrPath, type);
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

    public static AudioManager getAudioManager(World world) {
        return requireWorld(world).audio();
    }

    /** {@code PlaySound2D} — play a registered AUDIO asset. */
    public static boolean playSound2D(World world, String idOrPath) {
        return getAudioManager(world).play2D(idOrPath);
    }

    public static boolean playSound2D(World world, String idOrPath, float volumeScale) {
        return getAudioManager(world).play2D(idOrPath, volumeScale);
    }

    public static boolean stopSound(World world, String idOrPath) {
        return getAudioManager(world).stop(idOrPath);
    }

    public static boolean isSoundPlaying(World world, String idOrPath) {
        return getAudioManager(world).isPlaying(idOrPath);
    }

    public static void setMasterVolume(World world, float volume) {
        getAudioManager(world).setMasterVolume(volume);
    }

    public static float getMasterVolume(World world) {
        return getAudioManager(world).masterVolume();
    }

    public static WidgetViewport getWidgetViewport(World world) {
        return requireWorld(world).viewport();
    }

    public static <T extends Widget> T createWidget(
            World world, Class<T> type, String name) {
        return getWidgetViewport(world).createWidget(type, name);
    }

    public static Widget createWidget(World world, String typeName, String name) {
        return getWidgetViewport(world).createWidget(typeName, name);
    }

    public static boolean addToViewport(World world, Widget widget) {
        return getWidgetViewport(world).addToViewport(widget);
    }

    public static boolean addToViewport(World world, String name) {
        Widget widget = findWidget(world, name);
        return widget != null && widget.addToViewport();
    }

    public static boolean removeFromParent(World world, Widget widget) {
        return getWidgetViewport(world).removeFromParent(widget);
    }

    public static boolean removeFromParent(World world, String name) {
        Widget widget = findWidget(world, name);
        return widget != null && widget.removeFromParent();
    }

    public static boolean showWidget(World world, String name) {
        return getWidgetViewport(world).show(name);
    }

    public static boolean hideWidget(World world, String name) {
        return getWidgetViewport(world).hide(name);
    }

    public static Widget findWidget(World world, String name) {
        return getWidgetViewport(world).find(name);
    }

    public static CollisionWorld getCollisionWorld(World world) {
        return requireWorld(world).collision();
    }

    public static List<CollisionComponent> queryOverlaps(
            World world, CollisionComponent component) {
        return getCollisionWorld(world).queryOverlaps(component);
    }

    public static boolean isOverlapping(
            World world, CollisionComponent a, CollisionComponent b) {
        return getCollisionWorld(world).isOverlapping(a, b);
    }

    public static int overlapCount(World world) {
        return getCollisionWorld(world).overlapCount();
    }

    public static InputSubsystem getInputSubsystem(World world) {
        return requireWorld(world).input();
    }

    public static PlayerController getPlayerController(World world) {
        return requireWorld(world).playerController();
    }

    public static DelegateHandle bindAction(
            World world,
            String action,
            InputTrigger trigger,
            Consumer<InputActionValue> listener) {
        return getInputSubsystem(world).bindAction(action, trigger, listener);
    }

    public static void unbindAction(World world, DelegateHandle handle) {
        getInputSubsystem(world).unbind(handle);
    }

    public static void injectKey(World world, String key, boolean down) {
        getInputSubsystem(world).injectKey(key, down);
    }

    public static void injectAxis(World world, String key, float x, float y) {
        getInputSubsystem(world).injectAxis(key, x, y);
    }

    public static InputActionValue actionValue(World world, String action) {
        return getInputSubsystem(world).actionValue(action);
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
