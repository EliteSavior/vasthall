package com.elitesavior.vasthall.engine;

/**
 * Content kinds the {@link AssetRegistry} can index.
 * Unreal analog: asset class ({@code UWorld}, {@code UStaticMesh},
 * {@code UTexture2D}, {@code USoundBase}).
 */
public enum AssetKind {
    LEVEL,
    MESH,
    TEXTURE,
    AUDIO
}
