package com.elitesavior.vasthall.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Owns {@link Actor}s and ticks them. Unreal mental model: {@code UWorld}.
 *
 * <p>Single-threaded: call spawn / destroy / tick from the same thread
 * (the activity frame callback).
 */
public final class World {
    private final List<Actor> living = new ArrayList<>();
    private final List<Actor> pendingAdd = new ArrayList<>();
    private final List<Actor> pendingKill = new ArrayList<>();
    private long nextId = 1L;
    private boolean ticking;
    private int frameCount;

    public <T extends Actor> T spawnActor(Class<T> type) {
        return spawnActor(type, Transform.identity());
    }

    public <T extends Actor> T spawnActor(Class<T> type, Transform transform) {
        T actor;
        try {
            actor = type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException failed) {
            throw new IllegalArgumentException(
                    "Actor type needs a public no-arg constructor: " + type.getName(),
                    failed);
        }
        return spawnActor(actor, transform);
    }

    public <T extends Actor> T spawnActor(T actor, Transform transform) {
        if (actor == null) {
            throw new IllegalArgumentException("actor");
        }
        if (actor.world() != null) {
            throw new IllegalStateException("actor already belongs to a World");
        }
        Transform spawn = transform == null ? Transform.identity() : transform.copy();
        actor.attach(this, nextId++, spawn);
        if (ticking) {
            pendingAdd.add(actor);
        } else {
            living.add(actor);
            actor.callBeginPlay();
        }
        return actor;
    }

    public boolean destroyActor(Actor actor) {
        if (actor == null || actor.isPendingKill() || actor.world() != this) {
            return false;
        }
        actor.markPendingKill();
        pendingKill.add(actor);
        if (!ticking) {
            flushPending();
        }
        return true;
    }

    public void destroyAll() {
        List<Actor> snapshot = new ArrayList<>(living.size() + pendingAdd.size());
        snapshot.addAll(living);
        snapshot.addAll(pendingAdd);
        for (Actor actor : snapshot) {
            destroyActor(actor);
        }
        if (ticking) {
            return;
        }
        flushPending();
    }

    public void tick(float deltaSeconds) {
        ticking = true;
        try {
            for (int i = 0; i < living.size(); i++) {
                Actor actor = living.get(i);
                if (!actor.isPendingKill() && actor.isActorTickEnabled()) {
                    actor.callTick(deltaSeconds);
                }
            }
        } finally {
            ticking = false;
            flushPending();
            frameCount++;
        }
    }

    public int actorCount() {
        int count = 0;
        for (Actor actor : living) {
            if (!actor.isPendingKill()) {
                count++;
            }
        }
        for (Actor actor : pendingAdd) {
            if (!actor.isPendingKill()) {
                count++;
            }
        }
        return count;
    }

    public int frameCount() {
        return frameCount;
    }

    /** Mutable copy of living actors. Clearing the list does not change the world. */
    public List<Actor> actors() {
        List<Actor> copy = new ArrayList<>(living.size());
        for (Actor actor : living) {
            if (!actor.isPendingKill()) {
                copy.add(actor);
            }
        }
        for (Actor actor : pendingAdd) {
            if (!actor.isPendingKill()) {
                copy.add(actor);
            }
        }
        return copy;
    }

    public Actor findActor(String name) {
        if (name == null) {
            return null;
        }
        for (Actor actor : actors()) {
            if (name.equals(actor.name())) {
                return actor;
            }
        }
        return null;
    }

    public <T extends Actor> List<T> actorsOf(Class<T> type) {
        List<T> matched = new ArrayList<>();
        for (Actor actor : actors()) {
            if (type.isInstance(actor)) {
                matched.add(type.cast(actor));
            }
        }
        return matched;
    }

    public void appendDump(StringBuilder out) {
        out.append("world.actors=").append(actorCount()).append('\n');
        out.append("world.frame=").append(frameCount).append('\n');
        for (Actor actor : actors()) {
            Transform t = actor.transform();
            out.append("actor id=").append(actor.id())
                    .append(" name=").append(actor.name())
                    .append(" class=").append(actor.getClass().getSimpleName())
                    .append(" tick=").append(actor.isActorTickEnabled() ? 1 : 0)
                    .append(" loc=").append(fmt(t.location.x)).append(',')
                    .append(fmt(t.location.y)).append(',').append(fmt(t.location.z))
                    .append(" rot=").append(fmt(t.rotation.pitch)).append(',')
                    .append(fmt(t.rotation.yaw)).append(',').append(fmt(t.rotation.roll))
                    .append(" scale=").append(fmt(t.scale.x)).append(',')
                    .append(fmt(t.scale.y)).append(',').append(fmt(t.scale.z))
                    .append('\n');
        }
    }

    private void flushPending() {
        if (!pendingKill.isEmpty()) {
            for (Actor actor : pendingKill) {
                living.remove(actor);
                pendingAdd.remove(actor);
                actor.callEndPlay();
                actor.detach();
            }
            pendingKill.clear();
        }
        if (pendingAdd.isEmpty()) {
            return;
        }
        List<Actor> added = new ArrayList<>(pendingAdd);
        pendingAdd.clear();
        for (Actor actor : added) {
            if (actor.isPendingKill()) {
                continue;
            }
            living.add(actor);
            actor.callBeginPlay();
        }
    }

    private static String fmt(float value) {
        return String.format(Locale.US, "%.4f", value);
    }
}
