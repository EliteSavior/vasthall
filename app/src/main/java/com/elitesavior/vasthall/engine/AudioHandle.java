package com.elitesavior.vasthall.engine;

/**
 * Stub audio asset. No mixer or file decode lives here yet — register a
 * handle so levels/actors can resolve it the same way as meshes and textures.
 */
public final class AudioHandle {
    private final String id;

    public AudioHandle(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("audio id");
        }
        this.id = id.trim();
    }

    public String id() {
        return id;
    }
}
