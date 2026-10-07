package com.elitesavior.vasthall.engine;

/** 3-float vector used by {@link Transform} location and scale. */
public final class Vec3 {
    public float x;
    public float y;
    public float z;

    public Vec3() {
    }

    public Vec3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void copyFrom(Vec3 other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
    }

    public Vec3 copy() {
        return new Vec3(x, y, z);
    }
}
