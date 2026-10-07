package com.elitesavior.vasthall.engine;

import java.util.Objects;

/**
 * Hierarchical dotted tag. Unreal analog: {@code FGameplayTag}.
 *
 * <p>{@code Character.Status.Burning} is a child of {@code Character.Status}
 * and of {@code Character}. Matching is parent-granted-by-child: a container
 * that holds the child answers {@code hasTag} for each ancestor.
 */
public final class GameplayTag implements Comparable<GameplayTag> {
    public static final GameplayTag CHARACTER_PLAYER = of("Character.Player");
    public static final GameplayTag WORLD_LANDMARK_BEACON = of("World.Landmark.Beacon");
    public static final GameplayTag ITEM_WEAPON_MELEE = of("Item.Weapon.Melee");

    private final String name;
    private final GameplayTag parent;
    private final int depth;

    private GameplayTag(String name, GameplayTag parent) {
        this.name = name;
        this.parent = parent;
        this.depth = parent == null ? 1 : parent.depth + 1;
    }

    public static GameplayTag of(String dotted) {
        if (dotted == null || dotted.trim().isEmpty()) {
            throw new IllegalArgumentException("tag");
        }
        String trimmed = dotted.trim();
        if (trimmed.charAt(0) == '.' || trimmed.charAt(trimmed.length() - 1) == '.') {
            throw new IllegalArgumentException("tag");
        }
        String[] parts = trimmed.split("\\.", -1);
        StringBuilder built = new StringBuilder();
        GameplayTag current = null;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (!isSegment(part)) {
                throw new IllegalArgumentException("tag");
            }
            if (i > 0) {
                built.append('.');
            }
            built.append(part);
            current = new GameplayTag(built.toString(), current);
        }
        return current;
    }

    private static boolean isSegment(String part) {
        if (part == null || part.isEmpty()) {
            return false;
        }
        char first = part.charAt(0);
        if (!(first == '_' || (first >= 'A' && first <= 'Z') || (first >= 'a' && first <= 'z'))) {
            return false;
        }
        for (int i = 1; i < part.length(); i++) {
            char c = part.charAt(i);
            if (!(c == '_'
                    || (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        return true;
    }

    public String name() {
        return name;
    }

    public GameplayTag parent() {
        return parent;
    }

    public int depth() {
        return depth;
    }

    public boolean isChildOf(GameplayTag ancestor) {
        if (ancestor == null) {
            return false;
        }
        GameplayTag walk = parent;
        while (walk != null) {
            if (walk.equals(ancestor)) {
                return true;
            }
            walk = walk.parent;
        }
        return false;
    }

    public boolean isParentOf(GameplayTag descendant) {
        return descendant != null && descendant.isChildOf(this);
    }

    @Override
    public int compareTo(GameplayTag other) {
        return name.compareTo(other.name);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GameplayTag that)) {
            return false;
        }
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
