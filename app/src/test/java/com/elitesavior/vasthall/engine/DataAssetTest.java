package com.elitesavior.vasthall.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

public final class DataAssetTest {
    private AssetRegistry registry;

    @Before
    public void setUp() {
        registry = new AssetRegistry();
    }

    @Test
    public void namedTypedDataAssetRejectsBlankName() {
        WeaponDataAsset blade = WeaponDataAsset.hallBlade();
        assertEquals("HallBlade", blade.name());
        assertEquals("WeaponDataAsset", blade.assetType());
        assertEquals(25.0f, blade.damage(), 0.0001f);
        assertEquals(0.4f, blade.fireInterval(), 0.0001f);
        assertEquals(1, blade.magazineSize());
        try {
            new WeaponDataAsset("  ", 1.0f, 0.1f, 1);
            fail("expected blank name");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("data asset name"));
        }
    }

    @Test
    public void registerThenLoadDataAssetByIdAndPath() {
        WeaponDataAsset blade = new WeaponDataAsset("SideBlade", 10.0f, 0.2f, 6);
        Asset registered = registry.registerDataAsset(
                "SideBlade",
                "/Game/Data/SideBlade",
                blade);

        assertEquals("SideBlade", registered.id());
        assertEquals("/Game/Data/SideBlade", registered.path());
        assertEquals(AssetKind.DATA, registered.kind());
        assertSame(blade, registered.payload());
        assertSame(blade, registered.as(WeaponDataAsset.class));
        assertSame(blade, registered.as(DataAsset.class));
        assertSame(blade, registry.findDataAsset("SideBlade"));
        assertSame(blade, registry.findDataAsset("/Game/Data/SideBlade", WeaponDataAsset.class));
        assertSame(registered, registry.find("SideBlade"));
        assertTrue(registry.contains("/Game/Data/SideBlade"));
        assertEquals(1, registry.assetsOf(AssetKind.DATA).size());
    }

    @Test
    public void registerDataAssetUsesNameAndDefaultPath() {
        WeaponDataAsset blade = new WeaponDataAsset("ArenaBlade", 12.0f, 0.5f, 3);
        Asset registered = registry.registerDataAsset(blade);
        assertEquals("ArenaBlade", registered.id());
        assertEquals("/Game/Data/ArenaBlade", registered.path());
        assertEquals(AssetKind.DATA, registered.kind());
        assertSame(blade, registry.requireDataAsset("ArenaBlade", WeaponDataAsset.class));
    }

    @Test
    public void missingDataAssetReturnsNullAndRequireThrows() {
        assertNull(registry.findDataAsset("Missing"));
        assertNull(registry.findDataAsset(null));
        assertNull(registry.findDataAsset(""));
        assertNull(registry.findDataAsset("Missing", WeaponDataAsset.class));
        try {
            registry.requireDataAsset("Missing", WeaponDataAsset.class);
            fail("expected unknown data asset");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Missing"));
        }
    }

    @Test
    public void registerRejectsNonDataAssetPayloadForDataKind() {
        try {
            registry.register("Bad", "/Game/Data/Bad", AssetKind.DATA, new MeshHandle("Bad"));
            fail("expected DATA payload check");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("DataAsset"));
        }
        assertEquals(0, registry.size());
    }

    @Test
    public void demoCatalogRegistersHallBladeAndMappingAsDataAssets() {
        registry.registerDemoAssets();

        assertEquals(7, registry.size());
        WeaponDataAsset blade = registry.requireDataAsset(
                AssetRegistry.HALL_BLADE_ID, WeaponDataAsset.class);
        assertEquals(AssetRegistry.HALL_BLADE_PATH, registry.require(AssetRegistry.HALL_BLADE_ID).path());
        assertEquals(AssetKind.DATA, registry.require(AssetRegistry.HALL_BLADE_ID).kind());
        assertEquals(25.0f, blade.damage(), 0.0001f);
        assertSame(blade, registry.findDataAsset(AssetRegistry.HALL_BLADE_PATH));

        InputMappingContext mapping = registry.requireDataAsset(
                AssetRegistry.DEFAULT_MAPPING_ID, InputMappingContext.class);
        assertTrue(mapping instanceof DataAsset);
        assertEquals("Default", mapping.name());
        assertEquals("InputMappingContext", mapping.assetType());
        assertEquals(AssetKind.INPUT_MAPPING, registry.require(AssetRegistry.DEFAULT_MAPPING_ID).kind());
    }

    @Test
    public void gameplayStaticsLoadDataAssetThroughWorldRegistry() {
        World world = new World(AssetRegistry.withDemoAssets());
        WeaponDataAsset blade = GameplayStatics.loadDataAsset(
                world, AssetRegistry.HALL_BLADE_ID, WeaponDataAsset.class);
        assertNotNull(blade);
        assertEquals("HallBlade", blade.name());
        assertSame(blade, GameplayStatics.findDataAsset(world, AssetRegistry.HALL_BLADE_PATH));
        assertSame(
                blade,
                GameplayStatics.loadAsset(world, AssetRegistry.HALL_BLADE_ID).as(WeaponDataAsset.class));

        Actor actor = world.spawnActor(Actor.class);
        assertSame(blade, actor.loadAsset(AssetRegistry.HALL_BLADE_PATH, WeaponDataAsset.class));
        assertNull(actor.loadAsset(AssetRegistry.HALL_BLADE_ID, InputMappingContext.class));
    }

    @Test
    public void requireDataAssetRejectsWrongSubclass() {
        registry.registerDemoAssets();
        assertNull(registry.findDataAsset(AssetRegistry.HALL_BLADE_ID, InputMappingContext.class));
        try {
            registry.requireDataAsset(AssetRegistry.HALL_BLADE_ID, InputMappingContext.class);
            fail("expected type mismatch");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("type mismatch"));
            assertTrue(expected.getMessage().contains(AssetRegistry.HALL_BLADE_ID));
            assertTrue(expected.getMessage().contains("WeaponDataAsset"));
        }
    }

    @Test
    public void registerDataAssetInfersInputMappingKind() {
        InputMappingContext extra = new InputMappingContext("Side");
        extra.addAction(new InputAction("Sprint", InputValueType.DIGITAL));
        Asset registered = registry.registerDataAsset(extra);
        assertEquals("Side", registered.id());
        assertEquals("/Game/Data/Side", registered.path());
        assertEquals(AssetKind.INPUT_MAPPING, registered.kind());
        assertSame(extra, registry.requireDataAsset("Side", InputMappingContext.class));
    }

    @Test
    public void registerDataAssetRejectsNullAndWorldUsesRegisteredMapping() {
        try {
            registry.registerDataAsset(null);
            fail("expected data asset");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("data asset"));
        }
        try {
            registry.register("BadMap", "/Game/Input/Bad", AssetKind.INPUT_MAPPING, new MeshHandle("Bad"));
            fail("expected INPUT_MAPPING payload check");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("InputMappingContext"));
        }

        World world = new World(AssetRegistry.withDemoAssets());
        InputMappingContext registered = world.assets().requireDataAsset(
                AssetRegistry.DEFAULT_MAPPING_ID, InputMappingContext.class);
        assertSame(registered, world.input().mappingContexts().get(0));
        assertSame(
                registered,
                GameplayStatics.findDataAsset(
                        world, AssetRegistry.DEFAULT_MAPPING_ID, InputMappingContext.class));
    }
}
