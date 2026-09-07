package com.elitesavior.vasthall.engine;

/**
 * Sample weapon-stats DataAsset. Unreal analog: a custom
 * {@code UDataAsset} subclass with designer fields.
 *
 * <p>Stub numbers only — no fire / ammo runtime.
 */
public final class WeaponDataAsset extends DataAsset {
    private final float damage;
    private final float fireInterval;
    private final int magazineSize;

    public WeaponDataAsset(String name, float damage, float fireInterval, int magazineSize) {
        super(name);
        if (damage < 0.0f) {
            throw new IllegalArgumentException("damage");
        }
        if (fireInterval < 0.0f) {
            throw new IllegalArgumentException("fireInterval");
        }
        if (magazineSize < 0) {
            throw new IllegalArgumentException("magazineSize");
        }
        this.damage = damage;
        this.fireInterval = fireInterval;
        this.magazineSize = magazineSize;
    }

    /** Built-in Hall sample: melee stub at {@code /Game/Data/HallBlade}. */
    public static WeaponDataAsset hallBlade() {
        WeaponDataAsset blade = new WeaponDataAsset("HallBlade", 25.0f, 0.4f, 1);
        blade.gameplayTags().addTag(GameplayTag.ITEM_WEAPON_MELEE);
        return blade;
    }

    public float damage() {
        return damage;
    }

    public float fireInterval() {
        return fireInterval;
    }

    public int magazineSize() {
        return magazineSize;
    }
}
