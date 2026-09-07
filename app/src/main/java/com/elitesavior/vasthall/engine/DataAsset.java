package com.elitesavior.vasthall.engine;

/**
 * Named, typed data object. Unreal analog: {@code UDataAsset}.
 *
 * <p>Subclass this for designer-authored rows (weapon stats, mapping
 * contexts). Register the instance on {@link AssetRegistry}; look it
 * up by id or path. Not a World object — no tick, no spawn.
 */
public abstract class DataAsset {
    private final String name;

    protected DataAsset(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("data asset name");
        }
        this.name = name.trim();
    }

    public String name() {
        return name;
    }

    /** Unreal analog: the asset class name ({@code WeaponDataAsset}). */
    public String assetType() {
        return getClass().getSimpleName();
    }
}
