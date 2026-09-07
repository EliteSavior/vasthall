package com.elitesavior.vasthall.engine;

/**
 * Location, rotation, and scale for an {@link Actor}.
 * Mental model: Unreal {@code FTransform}.
 */
public final class Transform {
    public final Vec3 location;
    public final Rotator rotation;
    public final Vec3 scale;

    public Transform() {
        this.location = new Vec3();
        this.rotation = new Rotator();
        this.scale = new Vec3(1.0f, 1.0f, 1.0f);
    }

    public static Transform identity() {
        return new Transform();
    }

    public static Transform at(float x, float y, float z) {
        Transform transform = identity();
        transform.location.set(x, y, z);
        return transform;
    }

    public Transform copy() {
        Transform copy = identity();
        copy.copyFrom(this);
        return copy;
    }

    public void copyFrom(Transform other) {
        location.copyFrom(other.location);
        rotation.copyFrom(other.rotation);
        scale.copyFrom(other.scale);
    }
}
