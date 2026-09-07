package com.elitesavior.vasthall.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Named map of actor templates. Register on a {@link World} or
 * {@link AssetRegistry}, then {@link World#loadLevel(String)}. Unreal analog:
 * a {@code UWorld} asset / streaming level definition, not a live loaded
 * instance ({@link Level}).
 */
public final class LevelDefinition {
    public static final String HALL_RESOURCE = "levels/Hall.json";

    private final String name;
    private String gameModeClassName;
    private final List<ActorTemplate> actors = new ArrayList<>();

    private LevelDefinition(String name) {
        this.name = name;
    }

    public static LevelDefinition named(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("level name");
        }
        return new LevelDefinition(name.trim());
    }

    /** Built-in demo hall. Same content as {@code levels/Hall.json}. */
    public static LevelDefinition hall() {
        return fromResource(HALL_RESOURCE);
    }

    public static LevelDefinition fromResource(String path) {
        InputStream stream = LevelDefinition.class.getClassLoader().getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("missing level resource: " + path);
        }
        try {
            return LevelJson.parse(readUtf8(stream));
        } catch (IOException io) {
            throw new IllegalStateException("failed to read level resource: " + path, io);
        }
    }

    /** Short {@link GameMode} class name selected when this map is opened. */
    public LevelDefinition gameMode(String className) {
        if (className == null || className.trim().isEmpty()) {
            this.gameModeClassName = null;
            return this;
        }
        this.gameModeClassName = className.trim();
        return this;
    }

    public String gameModeClassName() {
        return gameModeClassName;
    }

    public LevelDefinition actor(ActorTemplate template) {
        if (template == null) {
            throw new IllegalArgumentException("actor template");
        }
        actors.add(template);
        return this;
    }

    public String name() {
        return name;
    }

    public List<ActorTemplate> actors() {
        return Collections.unmodifiableList(new ArrayList<>(actors));
    }

    private static String readUtf8(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
