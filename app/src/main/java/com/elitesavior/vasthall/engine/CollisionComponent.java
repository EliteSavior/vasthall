package com.elitesavior.vasthall.engine;

import java.util.Locale;

/**
 * Shape on an actor for overlap queries. Unreal mental model:
 * {@code UPrimitiveComponent} collision (GenerateOverlapEvents) without
 * a physics sim — world AABBs / spheres, rotation ignored.
 *
 * <p>Tick is off; {@link CollisionWorld} sweeps the registry each frame.
 */
public class CollisionComponent extends ActorComponent {
    public static final float DEFAULT_EXTENT = 0.5f;

    private CollisionShape shape = CollisionShape.BOX;
    private final Vec3 boxExtent = new Vec3(DEFAULT_EXTENT, DEFAULT_EXTENT, DEFAULT_EXTENT);
    private final Vec3 relativeLocation = new Vec3();
    private float sphereRadius = DEFAULT_EXTENT;
    private boolean generateOverlapEvents = true;
    private boolean collisionEnabled = true;

    public CollisionComponent() {
        setComponentTickEnabled(false);
    }

    public CollisionShape shape() {
        return shape;
    }

    public Vec3 boxExtent() {
        return boxExtent;
    }

    public Vec3 relativeLocation() {
        return relativeLocation;
    }

    public float sphereRadius() {
        return sphereRadius;
    }

    public CollisionComponent setBoxExtent(float x, float y, float z) {
        requireFinite("box extent", x, y, z);
        shape = CollisionShape.BOX;
        boxExtent.set(Math.abs(x), Math.abs(y), Math.abs(z));
        return this;
    }

    public CollisionComponent setSphereRadius(float radius) {
        requireFinite("sphere radius", radius);
        shape = CollisionShape.SPHERE;
        sphereRadius = Math.abs(radius);
        return this;
    }

    public CollisionComponent setRelativeLocation(float x, float y, float z) {
        requireFinite("relative location", x, y, z);
        relativeLocation.set(x, y, z);
        return this;
    }

    public boolean generateOverlapEvents() {
        return generateOverlapEvents;
    }

    public void setGenerateOverlapEvents(boolean enabled) {
        generateOverlapEvents = enabled;
    }

    public boolean isCollisionEnabled() {
        return collisionEnabled;
    }

    public void setCollisionEnabled(boolean enabled) {
        collisionEnabled = enabled;
    }

    public Vec3 worldCenter() {
        Actor actor = owner();
        if (actor == null) {
            return relativeLocation.copy();
        }
        Transform transform = actor.transform();
        return new Vec3(
                transform.location.x + relativeLocation.x * transform.scale.x,
                transform.location.y + relativeLocation.y * transform.scale.y,
                transform.location.z + relativeLocation.z * transform.scale.z);
    }

    public Aabb worldBounds() {
        Vec3 center = worldCenter();
        Vec3 extent = worldBoxExtent();
        return Aabb.fromCenterExtent(center.x, center.y, center.z, extent.x, extent.y, extent.z);
    }

    public float worldSphereRadius() {
        Actor actor = owner();
        if (actor == null) {
            return sphereRadius;
        }
        Vec3 scale = actor.transform().scale;
        float max = Math.max(Math.abs(scale.x), Math.max(Math.abs(scale.y), Math.abs(scale.z)));
        return sphereRadius * max;
    }

    public boolean overlaps(CollisionComponent other) {
        if (other == null || !collisionEnabled || !other.collisionEnabled) {
            return false;
        }
        if (shape == CollisionShape.SPHERE && other.shape == CollisionShape.SPHERE) {
            Vec3 a = worldCenter();
            Vec3 b = other.worldCenter();
            return Aabb.spheresOverlap(
                    a.x, a.y, a.z, worldSphereRadius(),
                    b.x, b.y, b.z, other.worldSphereRadius());
        }
        if (shape == CollisionShape.BOX && other.shape == CollisionShape.BOX) {
            return worldBounds().intersects(other.worldBounds());
        }
        CollisionComponent box = shape == CollisionShape.BOX ? this : other;
        CollisionComponent sphere = shape == CollisionShape.SPHERE ? this : other;
        Vec3 center = sphere.worldCenter();
        return box.worldBounds().intersectsSphere(
                center.x, center.y, center.z, sphere.worldSphereRadius());
    }

    private Vec3 worldBoxExtent() {
        Actor actor = owner();
        if (actor == null) {
            return boxExtent.copy();
        }
        Vec3 scale = actor.transform().scale;
        return new Vec3(
                boxExtent.x * Math.abs(scale.x),
                boxExtent.y * Math.abs(scale.y),
                boxExtent.z * Math.abs(scale.z));
    }

    @Override
    protected void appendDumpFields(StringBuilder out) {
        Vec3 center = worldCenter();
        out.append(" shape=").append(shape.name().toLowerCase(Locale.US))
                .append(" on=").append(collisionEnabled ? 1 : 0)
                .append(" events=").append(generateOverlapEvents ? 1 : 0)
                .append(" center=").append(fmt(center.x)).append(',')
                .append(fmt(center.y)).append(',').append(fmt(center.z));
        if (shape == CollisionShape.SPHERE) {
            out.append(" r=").append(fmt(worldSphereRadius()));
        } else {
            Vec3 extent = worldBoxExtent();
            out.append(" extent=").append(fmt(extent.x)).append(',')
                    .append(fmt(extent.y)).append(',').append(fmt(extent.z));
        }
    }

    private static void requireFinite(String label, float... values) {
        for (float value : values) {
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                throw new IllegalArgumentException(label);
            }
        }
    }

    private static String fmt(float value) {
        return String.format(Locale.US, "%.4f", value);
    }
}
