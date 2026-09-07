package com.elitesavior.vasthall.engine;

/**
 * One registered content entry. Unreal analog: an asset registry row
 * (object path + class) plus a loaded handle.
 *
 * <p>{@link #id()} is the short lookup key ({@code Hall}).
 * {@link #path()} is the Content Browser–lite path ({@code levels/Hall.json}
 * or {@code /Game/Textures/HallBeacon}).
 */
public final class Asset {
    private final String id;
    private final String path;
    private final AssetKind kind;
    private final Object payload;

    Asset(String id, String path, AssetKind kind, Object payload) {
        this.id = id;
        this.path = path;
        this.kind = kind;
        this.payload = payload;
    }

    public String id() {
        return id;
    }

    public String path() {
        return path;
    }

    public AssetKind kind() {
        return kind;
    }

    public Object payload() {
        return payload;
    }

    public <T> T as(Class<T> type) {
        if (type == null || !type.isInstance(payload)) {
            return null;
        }
        return type.cast(payload);
    }
}
