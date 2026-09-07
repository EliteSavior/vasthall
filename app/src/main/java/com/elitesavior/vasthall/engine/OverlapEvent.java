package com.elitesavior.vasthall.engine;

/**
 * Payload for {@link EventType#BEGIN_OVERLAP} / {@link EventType#END_OVERLAP}.
 * One event per pair (not once per side). Actor refs stay valid if a
 * component later detaches.
 */
public final class OverlapEvent {
    private final World world;
    private final CollisionComponent component;
    private final CollisionComponent otherComponent;
    private final Actor actor;
    private final Actor otherActor;

    public OverlapEvent(
            World world,
            CollisionComponent component,
            CollisionComponent otherComponent,
            Actor actor,
            Actor otherActor) {
        this.world = world;
        this.component = component;
        this.otherComponent = otherComponent;
        this.actor = actor;
        this.otherActor = otherActor;
    }

    public World world() {
        return world;
    }

    public CollisionComponent component() {
        return component;
    }

    public CollisionComponent otherComponent() {
        return otherComponent;
    }

    public Actor actor() {
        return actor;
    }

    public Actor otherActor() {
        return otherActor;
    }

    public boolean involves(Actor candidate) {
        return candidate != null && (candidate == actor || candidate == otherActor);
    }

    public boolean involves(CollisionComponent candidate) {
        return candidate != null && (candidate == component || candidate == otherComponent);
    }
}
