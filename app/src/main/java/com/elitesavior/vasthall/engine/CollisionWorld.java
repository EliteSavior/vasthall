package com.elitesavior.vasthall.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CPU overlap registry. Unreal mental model: world's collision queries +
 * Begin/End Overlap, without a physics scene.
 *
 * <p>{@link #sync()} diffs the current geometric pairs against last frame
 * and broadcasts {@link EventType#BEGIN_OVERLAP} / {@link EventType#END_OVERLAP}.
 */
public final class CollisionWorld {
    private static final class Pair {
        final CollisionComponent a;
        final CollisionComponent b;
        final Actor actorA;
        final Actor actorB;

        Pair(CollisionComponent first, CollisionComponent second, Actor firstOwner, Actor secondOwner) {
            this.a = first;
            this.b = second;
            this.actorA = firstOwner;
            this.actorB = secondOwner;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Pair that)) {
                return false;
            }
            return (a == that.a && b == that.b) || (a == that.b && b == that.a);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(a) ^ System.identityHashCode(b);
        }
    }

    private final World world;
    private final Map<Pair, Pair> active = new LinkedHashMap<>();
    private boolean debugDraw;

    public CollisionWorld(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world");
        }
        this.world = world;
    }

    public World world() {
        return world;
    }

    public boolean debugDraw() {
        return debugDraw;
    }

    /** Stub flag for a later debug draw; no renderer hook yet. */
    public void setDebugDraw(boolean enabled) {
        debugDraw = enabled;
    }

    public int overlapCount() {
        return active.size();
    }

    public List<OverlapEvent> overlaps() {
        List<OverlapEvent> copy = new ArrayList<>(active.size());
        for (Pair pair : active.values()) {
            copy.add(toEvent(pair));
        }
        return copy;
    }

    public boolean isOverlapping(CollisionComponent a, CollisionComponent b) {
        if (a == null || b == null || a == b) {
            return false;
        }
        return active.containsKey(new Pair(a, b, a.owner(), b.owner()));
    }

    public List<CollisionComponent> queryOverlaps(CollisionComponent query) {
        List<CollisionComponent> hits = new ArrayList<>();
        if (query == null || !query.isCollisionEnabled()) {
            return hits;
        }
        for (CollisionComponent other : collectEnabled()) {
            if (other == query || sameOwner(query, other)) {
                continue;
            }
            if (query.overlaps(other)) {
                hits.add(other);
            }
        }
        return hits;
    }

    public List<Actor> queryOverlappingActors(Actor actor) {
        List<Actor> hits = new ArrayList<>();
        if (actor == null) {
            return hits;
        }
        for (CollisionComponent component : actor.componentsOf(CollisionComponent.class)) {
            for (CollisionComponent other : queryOverlaps(component)) {
                Actor owner = other.owner();
                if (owner != null && !hits.contains(owner)) {
                    hits.add(owner);
                }
            }
        }
        return hits;
    }

    public void clear() {
        List<Pair> snapshot = new ArrayList<>(active.values());
        active.clear();
        for (Pair pair : snapshot) {
            broadcast(EventType.END_OVERLAP, pair);
        }
    }

    void sync() {
        Map<Pair, Pair> now = new LinkedHashMap<>();
        List<CollisionComponent> bodies = collectEnabled();
        for (int i = 0; i < bodies.size(); i++) {
            CollisionComponent a = bodies.get(i);
            for (int j = i + 1; j < bodies.size(); j++) {
                CollisionComponent b = bodies.get(j);
                if (sameOwner(a, b) || !a.overlaps(b)) {
                    continue;
                }
                if (!a.generateOverlapEvents() || !b.generateOverlapEvents()) {
                    continue;
                }
                Pair pair = new Pair(a, b, a.owner(), b.owner());
                now.put(pair, pair);
                if (!active.containsKey(pair)) {
                    broadcast(EventType.BEGIN_OVERLAP, pair);
                }
            }
        }
        for (Pair previous : active.values()) {
            if (!now.containsKey(previous)) {
                broadcast(EventType.END_OVERLAP, previous);
            }
        }
        active.clear();
        active.putAll(now);
    }

    void appendDump(StringBuilder out) {
        out.append("world.overlaps=").append(active.size())
                .append(" debugDraw=").append(debugDraw ? 1 : 0)
                .append('\n');
    }

    List<String> describe() {
        List<String> lines = new ArrayList<>();
        lines.add("debugDraw=" + (debugDraw ? 1 : 0));
        for (Pair pair : active.values()) {
            lines.add(String.format(
                    Locale.US,
                    "%s vs %s %s-%s",
                    nameOf(pair.actorA, pair.a),
                    nameOf(pair.actorB, pair.b),
                    pair.a.shape().name().toLowerCase(Locale.US),
                    pair.b.shape().name().toLowerCase(Locale.US)));
        }
        return lines;
    }

    private List<CollisionComponent> collectEnabled() {
        List<CollisionComponent> bodies = new ArrayList<>();
        for (Actor actor : world.actors()) {
            if (actor.isPendingKill()) {
                continue;
            }
            for (CollisionComponent component : actor.componentsOf(CollisionComponent.class)) {
                if (component.isAttached() && component.isCollisionEnabled()) {
                    bodies.add(component);
                }
            }
        }
        return bodies;
    }

    private OverlapEvent toEvent(Pair pair) {
        return new OverlapEvent(world, pair.a, pair.b, pair.actorA, pair.actorB);
    }

    private void broadcast(EventType<OverlapEvent> type, Pair pair) {
        world.events().broadcast(type, toEvent(pair));
    }

    private static boolean sameOwner(CollisionComponent a, CollisionComponent b) {
        Actor left = a.owner();
        Actor right = b.owner();
        return left != null && left == right;
    }

    private static String nameOf(Actor actor, CollisionComponent component) {
        if (actor != null) {
            return actor.name();
        }
        return component.getClass().getSimpleName();
    }
}
