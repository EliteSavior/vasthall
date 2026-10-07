package com.elitesavior.vasthall.engine;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Ordered unique set of {@link GameplayTag}s. Unreal analog:
 * {@code FGameplayTagContainer} — add / remove / has / has-all / has-any.
 *
 * <p>{@link #hasTag(GameplayTag)} is hierarchical (UE default, not exact):
 * owning {@code A.B.C} answers true for {@code A.B.C}, {@code A.B}, and
 * {@code A}. {@link #hasTagExact} is identity only.
 */
public final class GameplayTagContainer {
    private final LinkedHashSet<GameplayTag> tags = new LinkedHashSet<>();

    public GameplayTagContainer() {
    }

    public GameplayTagContainer(GameplayTag... initial) {
        if (initial == null) {
            return;
        }
        for (GameplayTag tag : initial) {
            addTag(tag);
        }
    }

    public static GameplayTagContainer of(String... dotted) {
        GameplayTagContainer container = new GameplayTagContainer();
        if (dotted == null) {
            return container;
        }
        for (String tag : dotted) {
            container.addTag(tag);
        }
        return container;
    }

    public boolean addTag(String dotted) {
        return addTag(GameplayTag.of(dotted));
    }

    public boolean addTag(GameplayTag tag) {
        if (tag == null) {
            throw new IllegalArgumentException("tag");
        }
        return tags.add(tag);
    }

    public boolean removeTag(String dotted) {
        if (dotted == null || dotted.trim().isEmpty()) {
            return false;
        }
        try {
            return removeTag(GameplayTag.of(dotted));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public boolean removeTag(GameplayTag tag) {
        return tag != null && tags.remove(tag);
    }

    public boolean hasTag(String dotted) {
        if (dotted == null || dotted.trim().isEmpty()) {
            return false;
        }
        try {
            return hasTag(GameplayTag.of(dotted));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /**
     * True when this container holds {@code tag} or a more specific child
     * of it (UE {@code HasTag} with exact-match off).
     */
    public boolean hasTag(GameplayTag tag) {
        if (tag == null) {
            return false;
        }
        for (GameplayTag owned : tags) {
            if (owned.equals(tag) || owned.isChildOf(tag)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasTagExact(String dotted) {
        if (dotted == null || dotted.trim().isEmpty()) {
            return false;
        }
        try {
            return hasTagExact(GameplayTag.of(dotted));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public boolean hasTagExact(GameplayTag tag) {
        return tag != null && tags.contains(tag);
    }

    public boolean hasAll(GameplayTagContainer other) {
        if (other == null || other.isEmpty()) {
            return true;
        }
        for (GameplayTag tag : other.tags) {
            if (!hasTag(tag)) {
                return false;
            }
        }
        return true;
    }

    public boolean hasAny(GameplayTagContainer other) {
        if (other == null || other.isEmpty()) {
            return false;
        }
        for (GameplayTag tag : other.tags) {
            if (hasTag(tag)) {
                return true;
            }
        }
        return false;
    }

    public boolean matches(GameplayTagQuery query) {
        return query != null && query.matches(this);
    }

    public void clear() {
        tags.clear();
    }

    public int size() {
        return tags.size();
    }

    public boolean isEmpty() {
        return tags.isEmpty();
    }

    /** Copy; mutating the set does not change the container. */
    public Set<GameplayTag> tags() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(tags));
    }

    String dump() {
        if (tags.isEmpty()) {
            return "-";
        }
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (GameplayTag tag : tags) {
            if (!first) {
                out.append(',');
            }
            out.append(tag.name());
            first = false;
        }
        return out.toString();
    }
}
