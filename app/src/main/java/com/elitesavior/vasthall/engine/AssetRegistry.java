package com.elitesavior.vasthall.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Content Browser–lite catalog. Unreal analog: {@code UAssetManager} /
 * Asset Registry — register once, look up by short id or object path.
 *
 * <p>Does not package, cook, or stream bytes. It indexes handles
 * ({@link LevelDefinition}, {@link MeshHandle}, {@link TextureHandle},
 * {@link AudioHandle}) so gameplay code stops hardcoding one-off loads.
 */
public final class AssetRegistry {
    public static final String HALL_LEVEL_ID = "Hall";
    public static final String HALL_LEVEL_PATH = LevelDefinition.HALL_RESOURCE;
    public static final String HALL_MESH_ID = "HallMesh";
    public static final String HALL_MESH_PATH = "/Game/Meshes/Hall";
    public static final String HALL_BEACON_TEXTURE_ID = "HallBeacon";
    public static final String HALL_BEACON_TEXTURE_PATH = "/Game/Textures/HallBeacon";
    public static final String HALL_AMBIENCE_ID = "HallAmbience";
    public static final String HALL_AMBIENCE_PATH = "/Game/Audio/HallAmbience";

    private final Map<String, Asset> byId = new LinkedHashMap<>();
    private final Map<String, String> aliasToId = new LinkedHashMap<>();

    public static AssetRegistry withDemoAssets() {
        AssetRegistry registry = new AssetRegistry();
        registry.registerDemoAssets();
        return registry;
    }

    /**
     * Built-in Hall sample: classpath {@code levels/Hall.json} plus mesh /
     * texture / audio stubs for the native hall.
     */
    public void registerDemoAssets() {
        registerLevel(LevelDefinition.hall());
        register(HALL_MESH_ID, HALL_MESH_PATH, AssetKind.MESH, new MeshHandle(HALL_MESH_ID));
        register(
                HALL_BEACON_TEXTURE_ID,
                HALL_BEACON_TEXTURE_PATH,
                AssetKind.TEXTURE,
                new TextureHandle(HALL_BEACON_TEXTURE_ID));
        register(
                HALL_AMBIENCE_ID,
                HALL_AMBIENCE_PATH,
                AssetKind.AUDIO,
                new AudioHandle(HALL_AMBIENCE_ID));
    }

    public Asset registerLevel(LevelDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("level definition");
        }
        return register(
                definition.name(),
                "levels/" + definition.name() + ".json",
                AssetKind.LEVEL,
                definition);
    }

    public Asset register(String id, AssetKind kind, Object payload) {
        return register(id, id, kind, payload);
    }

    public Asset register(String id, String path, AssetKind kind, Object payload) {
        String trimmedId = requireKey(id, "asset id");
        String trimmedPath = path == null || path.trim().isEmpty() ? trimmedId : path.trim();
        if (kind == null) {
            throw new IllegalArgumentException("asset kind");
        }
        if (payload == null) {
            throw new IllegalArgumentException("asset payload");
        }
        if (kind == AssetKind.LEVEL && !(payload instanceof LevelDefinition)) {
            throw new IllegalArgumentException("LEVEL payload must be LevelDefinition");
        }
        forget(trimmedId);
        if (!trimmedPath.equals(trimmedId)) {
            forget(trimmedPath);
        }
        Asset asset = new Asset(trimmedId, trimmedPath, kind, payload);
        byId.put(trimmedId, asset);
        if (!trimmedPath.equals(trimmedId)) {
            aliasToId.put(trimmedPath, trimmedId);
        }
        return asset;
    }

    public Asset find(String idOrPath) {
        if (idOrPath == null) {
            return null;
        }
        String key = idOrPath.trim();
        if (key.isEmpty()) {
            return null;
        }
        Asset direct = byId.get(key);
        if (direct != null) {
            return direct;
        }
        String id = aliasToId.get(key);
        return id == null ? null : byId.get(id);
    }

    public Asset require(String idOrPath) {
        Asset asset = find(idOrPath);
        if (asset == null) {
            throw new IllegalArgumentException("unknown asset: " + idOrPath);
        }
        return asset;
    }

    public boolean contains(String idOrPath) {
        return find(idOrPath) != null;
    }

    public LevelDefinition findLevel(String idOrPath) {
        Asset asset = find(idOrPath);
        if (asset == null || asset.kind() != AssetKind.LEVEL) {
            return null;
        }
        return asset.as(LevelDefinition.class);
    }

    public int size() {
        return byId.size();
    }

    /** Mutable copy. Clearing the list does not change the registry. */
    public List<Asset> assets() {
        return new ArrayList<>(byId.values());
    }

    public List<Asset> assetsOf(AssetKind kind) {
        List<Asset> matched = new ArrayList<>();
        if (kind == null) {
            return matched;
        }
        for (Asset asset : byId.values()) {
            if (asset.kind() == kind) {
                matched.add(asset);
            }
        }
        return matched;
    }

    void appendDump(StringBuilder out) {
        out.append("world.assets=").append(size()).append('\n');
        for (Asset asset : byId.values()) {
            out.append("asset id=").append(asset.id())
                    .append(" path=").append(asset.path())
                    .append(" kind=").append(asset.kind().name())
                    .append('\n');
        }
    }

    private void forget(String key) {
        Asset existing = find(key);
        if (existing == null) {
            return;
        }
        byId.remove(existing.id());
        aliasToId.values().removeIf(id -> id.equals(existing.id()));
        aliasToId.remove(existing.path());
    }

    private static String requireKey(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label);
        }
        return value.trim();
    }
}
