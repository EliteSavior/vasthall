package com.elitesavior.vasthall.engine;

/**
 * Stub texture asset. Native hall materials stay in {@code libvasthall.so};
 * this is the Java-side Content Browser handle gameplay code can resolve.
 */
public final class TextureHandle {
    private final String id;

    public TextureHandle(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("texture id");
        }
        this.id = id.trim();
    }

    public String id() {
        return id;
    }
}
