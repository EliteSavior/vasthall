package com.elitesavior.vasthall.engine;

/**
 * Unreal-style helpers over {@link World} level streaming.
 *
 * <p>{@code LoadStreamLevel} → {@link #loadLevel}; {@code UnloadStreamLevel}
 * → {@link #unloadLevel}; {@code OpenLevel} → {@link #openLevel} (same-world
 * travel: unload loaded streaming levels, then load the named map).
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
}
