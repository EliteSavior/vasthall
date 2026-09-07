package com.elitesavior.vasthall.engine;

/**
 * Payload for {@link EventType#ACTOR_SPAWNED} / {@link EventType#ACTOR_DESTROYED}.
 */
public final class ActorEvent {
    private final World world;
    private final Actor actor;

    public ActorEvent(World world, Actor actor) {
        this.world = world;
        this.actor = actor;
    }

    public World world() {
        return world;
    }

    public Actor actor() {
        return actor;
    }
}
