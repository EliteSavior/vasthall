package com.elitesavior.vasthall.engine;

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

    /** Soft lookup. Missing names return null. */
    public static Asset findAsset(World world, String idOrPath) {
        return requireWorld(world).assets().find(idOrPath);
    }

    /** Hard lookup. Missing names throw {@code unknown asset}. */
    public static Asset loadAsset(World world, String idOrPath) {
        return requireWorld(world).assets().require(idOrPath);
    }

    private static World requireWorld(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world");
        }
        return world;
    }
}
