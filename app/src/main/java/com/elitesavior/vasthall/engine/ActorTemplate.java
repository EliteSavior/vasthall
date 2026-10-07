package com.elitesavior.vasthall.engine;

/**
 * Spawn spec for one actor in a {@link LevelDefinition}.
 * Unreal analog: a placed actor record on a map.
 */
public final class ActorTemplate {
    private final String className;
    private String name;
    private final Transform transform = Transform.identity();
    private Boolean tickEnabled;

    private ActorTemplate(String className) {
        this.className = className;
    }

    public static ActorTemplate of(String className) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("actor class");
        }
        return new ActorTemplate(className);
    }

    public ActorTemplate named(String name) {
        this.name = name;
        return this;
    }

    public ActorTemplate at(float x, float y, float z) {
        transform.location.set(x, y, z);
        return this;
    }

    public ActorTemplate rotation(float pitch, float yaw, float roll) {
        transform.rotation.set(pitch, yaw, roll);
        return this;
    }

    public ActorTemplate scale(float x, float y, float z) {
        transform.scale.set(x, y, z);
        return this;
    }

    public ActorTemplate tickEnabled(boolean enabled) {
        this.tickEnabled = enabled;
        return this;
    }

    public String className() {
        return className;
    }

    public String name() {
        return name;
    }

    public Transform transform() {
        return transform.copy();
    }

    public Boolean tickEnabled() {
        return tickEnabled;
    }
}
