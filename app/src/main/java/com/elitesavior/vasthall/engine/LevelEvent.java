package com.elitesavior.vasthall.engine;

/**
 * Payload for {@link EventType#LEVEL_LOADED} / {@link EventType#LEVEL_UNLOADED}.
 */
public final class LevelEvent {
    private final World world;
    private final String levelName;

    public LevelEvent(World world, String levelName) {
        this.world = world;
        this.levelName = levelName;
    }

    public World world() {
        return world;
    }

    public String levelName() {
        return levelName;
    }
}
