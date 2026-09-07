package com.elitesavior.vasthall.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * A named level currently loaded into a {@link World}.
 * Unreal analog: a streaming {@code ULevel} instance.
 *
 * <p>Unload via {@link World#unloadLevel(String)} — do not destroy this object
 * yourself. The world owns the actor list.
 */
public final class Level {
    private final String name;
    private final List<Actor> owned = new ArrayList<>();

    Level(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public int actorCount() {
        int count = 0;
        for (Actor actor : owned) {
            if (!actor.isPendingKill()) {
                count++;
            }
        }
        return count;
    }

    public List<Actor> actors() {
        List<Actor> copy = new ArrayList<>(owned.size());
        for (Actor actor : owned) {
            if (!actor.isPendingKill()) {
                copy.add(actor);
            }
        }
        return copy;
    }

    public boolean contains(Actor actor) {
        if (actor == null || actor.isPendingKill()) {
            return false;
        }
        return owned.contains(actor);
    }

    void add(Actor actor) {
        owned.add(actor);
    }

    void remove(Actor actor) {
        owned.remove(actor);
    }

    void clear() {
        owned.clear();
    }
}
