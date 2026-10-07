package com.elitesavior.vasthall.engine;

/**
 * Axis-aligned box used by {@link CollisionComponent}. Inclusive on
 * touching faces — no PhysX / GJK, just min/max tests.
 */
public final class Aabb {
    public final Vec3 min = new Vec3();
    public final Vec3 max = new Vec3();

    public static Aabb fromCenterExtent(
            float cx, float cy, float cz, float ex, float ey, float ez) {
        Aabb box = new Aabb();
        float hx = Math.abs(ex);
        float hy = Math.abs(ey);
        float hz = Math.abs(ez);
        box.min.set(cx - hx, cy - hy, cz - hz);
        box.max.set(cx + hx, cy + hy, cz + hz);
        return box;
    }

    public boolean intersects(Aabb other) {
        if (other == null) {
            return false;
        }
        return min.x <= other.max.x && max.x >= other.min.x
                && min.y <= other.max.y && max.y >= other.min.y
                && min.z <= other.max.z && max.z >= other.min.z;
    }

    public Vec3 closestPoint(float x, float y, float z) {
        return new Vec3(clamp(x, min.x, max.x), clamp(y, min.y, max.y), clamp(z, min.z, max.z));
    }

    public boolean intersectsSphere(float cx, float cy, float cz, float radius) {
        Vec3 closest = closestPoint(cx, cy, cz);
        float dx = closest.x - cx;
        float dy = closest.y - cy;
        float dz = closest.z - cz;
        float r = Math.abs(radius);
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    public static boolean spheresOverlap(
            float ax, float ay, float az, float ar,
            float bx, float by, float bz, float br) {
        float dx = ax - bx;
        float dy = ay - by;
        float dz = az - bz;
        float r = Math.abs(ar) + Math.abs(br);
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    private static float clamp(float value, float lo, float hi) {
        if (value < lo) {
            return lo;
        }
        if (value > hi) {
            return hi;
        }
        return value;
    }
}
