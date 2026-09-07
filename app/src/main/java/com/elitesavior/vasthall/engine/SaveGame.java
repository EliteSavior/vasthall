package com.elitesavior.vasthall.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Structured save payload. Unreal mental model: {@code USaveGame} —
 * a small object you fill, then persist with {@code SaveGameToSlot}.
 *
 * <p>This version stores the current level name, GameMode class, and a
 * snapshot of live actor transforms / tick / tags. It does not serialize
 * native renderer state.
 */
public final class SaveGame {
    public static final int FORMAT_VERSION = 1;

    private int version = FORMAT_VERSION;
    private String levelName = "";
    private String gameMode = "";
    private final List<ActorRecord> actors = new ArrayList<>();

    public int version() {
        return version;
    }

    public String levelName() {
        return levelName;
    }

    public String gameMode() {
        return gameMode;
    }

    /** Mutable copy. Clearing the list does not change this save. */
    public List<ActorRecord> actors() {
        return new ArrayList<>(actors);
    }

    public ActorRecord findActor(String name) {
        if (name == null) {
            return null;
        }
        for (ActorRecord actor : actors) {
            if (name.equals(actor.name())) {
                return actor;
            }
        }
        return null;
    }

    /** Snapshot the world's current level and actor gameplay fields. */
    public static SaveGame capture(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world");
        }
        SaveGame save = new SaveGame();
        save.version = FORMAT_VERSION;
        List<Level> levels = world.loadedLevels();
        save.levelName = levels.isEmpty() ? "" : levels.get(0).name();
        GameMode mode = world.gameMode();
        save.gameMode = mode == null ? "" : mode.getClass().getSimpleName();
        for (Actor actor : world.actors()) {
            save.actors.add(ActorRecord.from(actor));
        }
        return save;
    }

    /**
     * Travel to the saved level (when named) and restore matching actors
     * by name. Actors that are not in the reloaded map are skipped.
     */
    public boolean apply(GameInstance game) {
        if (game == null) {
            throw new IllegalArgumentException("game");
        }
        if (levelName != null && !levelName.isEmpty()) {
            game.openLevel(levelName);
        }
        World world = game.world();
        for (ActorRecord record : actors) {
            Actor actor = world.findActor(record.name());
            if (actor != null) {
                record.applyTo(actor);
            }
        }
        return true;
    }

    public String toJson() {
        return SaveGameJson.write(this);
    }

    public static SaveGame parse(String json) {
        return SaveGameJson.parse(json);
    }

    void setVersion(int version) {
        this.version = version;
    }

    void setLevelName(String levelName) {
        this.levelName = levelName == null ? "" : levelName;
    }

    void setGameMode(String gameMode) {
        this.gameMode = gameMode == null ? "" : gameMode;
    }

    void addActor(ActorRecord actor) {
        actors.add(actor);
    }

    /** One actor's persistable gameplay fields. */
    public static final class ActorRecord {
        private String name = "";
        private String className = "";
        private String levelName = "";
        private final Vec3 location = new Vec3();
        private final Rotator rotation = new Rotator();
        private final Vec3 scale = new Vec3(1.0f, 1.0f, 1.0f);
        private boolean tickEnabled = true;
        private final List<String> tags = new ArrayList<>();

        public String name() {
            return name;
        }

        public String className() {
            return className;
        }

        public String levelName() {
            return levelName;
        }

        public Vec3 location() {
            return location.copy();
        }

        public Rotator rotation() {
            return rotation.copy();
        }

        public Vec3 scale() {
            return scale.copy();
        }

        public boolean tickEnabled() {
            return tickEnabled;
        }

        public List<String> tags() {
            return Collections.unmodifiableList(new ArrayList<>(tags));
        }

        static ActorRecord from(Actor actor) {
            ActorRecord record = new ActorRecord();
            record.name = actor.name();
            record.className = actor.getClass().getSimpleName();
            record.levelName = actor.levelName() == null ? "" : actor.levelName();
            Transform transform = actor.transform();
            record.location.copyFrom(transform.location);
            record.rotation.copyFrom(transform.rotation);
            record.scale.copyFrom(transform.scale);
            record.tickEnabled = actor.isActorTickEnabled();
            TagComponent tagComponent = actor.getComponent(TagComponent.class);
            if (tagComponent != null) {
                record.tags.addAll(tagComponent.tags());
            }
            return record;
        }

        void applyTo(Actor actor) {
            actor.transform().location.copyFrom(location);
            actor.transform().rotation.copyFrom(rotation);
            actor.transform().scale.copyFrom(scale);
            actor.setActorTickEnabled(tickEnabled);
            if (!tags.isEmpty() || actor.getComponent(TagComponent.class) != null) {
                TagComponent tagComponent = actor.getComponent(TagComponent.class);
                if (tagComponent == null) {
                    tagComponent = actor.addComponent(new TagComponent());
                }
                for (String existing : new ArrayList<>(tagComponent.tags())) {
                    tagComponent.removeTag(existing);
                }
                for (String tag : tags) {
                    tagComponent.addTag(tag);
                }
            }
        }

        void setName(String name) {
            this.name = name == null ? "" : name;
        }

        void setClassName(String className) {
            this.className = className == null ? "" : className;
        }

        void setLevelName(String levelName) {
            this.levelName = levelName == null ? "" : levelName;
        }

        void setLocation(float x, float y, float z) {
            location.set(x, y, z);
        }

        void setRotation(float pitch, float yaw, float roll) {
            rotation.set(pitch, yaw, roll);
        }

        void setScale(float x, float y, float z) {
            scale.set(x, y, z);
        }

        void setTickEnabled(boolean tickEnabled) {
            this.tickEnabled = tickEnabled;
        }

        void addTag(String tag) {
            if (tag != null && !tag.isEmpty()) {
                tags.add(tag);
            }
        }
    }
}
