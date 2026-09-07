package com.elitesavior.vasthall.engine;

/**
 * Lightweight match expression over a {@link GameplayTagContainer}.
 * Unreal analog: {@code FGameplayTagQuery} (All / Any / None, not the
 * full expression editor).
 *
 * <p>A container matches when it {@link GameplayTagContainer#hasAll has
 * all} required tags, {@link GameplayTagContainer#hasAny has any} of the
 * optional tags when that list is non-empty, and has none of the excluded
 * tags.
 */
public final class GameplayTagQuery {
    private final GameplayTagContainer all;
    private final GameplayTagContainer any;
    private final GameplayTagContainer none;

    private GameplayTagQuery(
            GameplayTagContainer all,
            GameplayTagContainer any,
            GameplayTagContainer none) {
        this.all = all;
        this.any = any;
        this.none = none;
    }

    public static GameplayTagQuery all(String... tags) {
        return empty().withAll(tags);
    }

    public static GameplayTagQuery any(String... tags) {
        return empty().withAny(tags);
    }

    public static GameplayTagQuery none(String... tags) {
        return empty().withNone(tags);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static GameplayTagQuery empty() {
        return new GameplayTagQuery(
                new GameplayTagContainer(),
                new GameplayTagContainer(),
                new GameplayTagContainer());
    }

    public boolean matches(GameplayTagContainer container) {
        if (container == null) {
            return false;
        }
        if (!all.isEmpty() && !container.hasAll(all)) {
            return false;
        }
        if (!any.isEmpty() && !container.hasAny(any)) {
            return false;
        }
        if (!none.isEmpty() && container.hasAny(none)) {
            return false;
        }
        return true;
    }

    private GameplayTagQuery withAll(String... tags) {
        addAll(all, tags);
        return this;
    }

    private GameplayTagQuery withAny(String... tags) {
        addAll(any, tags);
        return this;
    }

    private GameplayTagQuery withNone(String... tags) {
        addAll(none, tags);
        return this;
    }

    private static void addAll(GameplayTagContainer target, String... tags) {
        if (tags == null) {
            return;
        }
        for (String tag : tags) {
            target.addTag(tag);
        }
    }

    public static final class Builder {
        private final GameplayTagContainer all = new GameplayTagContainer();
        private final GameplayTagContainer any = new GameplayTagContainer();
        private final GameplayTagContainer none = new GameplayTagContainer();

        public Builder all(String... tags) {
            addAll(this.all, tags);
            return this;
        }

        public Builder any(String... tags) {
            addAll(this.any, tags);
            return this;
        }

        public Builder none(String... tags) {
            addAll(this.none, tags);
            return this;
        }

        public GameplayTagQuery build() {
            return new GameplayTagQuery(all, any, none);
        }
    }
}
