package com.elitesavior.vasthall.engine;

import java.util.Locale;

/**
 * Stub locomotion: each tick adds {@code velocity * dt} to the owner actor's
 * location. Native hall walk/look still live in {@code libvasthall.so}; this
 * is the Java-side Unreal {@code UMovementComponent} analog for spawned
 * actors that should slide in the Scene layer.
 */
public class MovementComponent extends ActorComponent {
    private final Vec3 velocity = new Vec3();

    public Vec3 velocity() {
        return velocity;
    }

    public void setVelocity(float x, float y, float z) {
        velocity.set(x, y, z);
    }

    @Override
    protected void tick(float deltaSeconds) {
        Actor actor = owner();
        if (actor == null) {
            return;
        }
        Vec3 location = actor.transform().location;
        location.x += velocity.x * deltaSeconds;
        location.y += velocity.y * deltaSeconds;
        location.z += velocity.z * deltaSeconds;
    }

    @Override
    protected void appendDumpFields(StringBuilder out) {
        out.append(" vel=").append(fmt(velocity.x)).append(',')
                .append(fmt(velocity.y)).append(',').append(fmt(velocity.z));
    }

    private static String fmt(float value) {
        return String.format(Locale.US, "%.4f", value);
    }
}
