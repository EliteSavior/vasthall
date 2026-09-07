package com.elitesavior.vasthall.engine;

/**
 * Stub mesh asset. The playable hall mesh is still owned by
 * {@code libvasthall.so}; this handle names it for the registry.
 */
public final class MeshHandle {
    private final String id;

    public MeshHandle(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("mesh id");
        }
        this.id = id.trim();
    }

    public String id() {
        return id;
    }
}
