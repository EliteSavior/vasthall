package com.elitesavior.vasthall.engine;

/**
 * Euler rotation in degrees, Unreal {@code FRotator} order: pitch, yaw, roll.
 * Pitch is X, yaw is Z (turn), roll is Y.
 */
public final class Rotator {
    public float pitch;
    public float yaw;
    public float roll;

    public Rotator() {
    }

    public Rotator(float pitch, float yaw, float roll) {
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
    }

    public void set(float pitch, float yaw, float roll) {
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
    }

    public void copyFrom(Rotator other) {
        this.pitch = other.pitch;
        this.yaw = other.yaw;
        this.roll = other.roll;
    }

    public Rotator copy() {
        return new Rotator(pitch, yaw, roll);
    }
}
