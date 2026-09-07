package com.elitesavior.vasthall.engine;

/**
 * Unreal-style helpers over {@link World} level streaming.
 *
 * <p>{@code LoadStreamLevel} → {@link #loadLevel}; {@code UnloadStreamLevel}
 * → {@link #unloadLevel}; {@code OpenLevel} → {@link #openLevel} (same-world
 * travel: unload loaded streaming levels, then load the named map).
 * {@link #findAsset} / {@link #loadAsset} look up the world's
 * {@link AssetRegistry}.
 */
public final class GameplayStatics {
    private GameplayStatics() {
    }

    public static Level loadLevel(World world, String name) {
        return world.loadLevel(name);
    }

    public static boolean unloadLevel(World world, String name) {
        return world.unloadLevel(name);
    }

    public static Level openLevel(World world, String name) {
        return world.openLevel(name);
    }

    /** Soft lookup. Missing names return null. */
    public static Asset findAsset(World world, String idOrPath) {
        if (world == null) {
            throw new IllegalArgumentException("world");
        }
        return world.assets().find(idOrPath);
    }

    /** Hard lookup. Missing names throw {@code unknown asset}. */
    public static Asset loadAsset(World world, String idOrPath) {
        if (world == null) {
            throw new IllegalArgumentException("world");
        }
        return world.assets().require(idOrPath);
    }
}
