package com.elitesavior.vasthall.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

public final class AssetRegistryTest {
    private AssetRegistry registry;

    @Before
    public void setUp() {
        registry = new AssetRegistry();
    }

    @Test
    public void registerThenLookupByIdAndPath() {
        LevelDefinition hall = LevelDefinition.named("Hall");
        Asset registered = registry.register(
                "Hall",
                "levels/Hall.json",
                AssetKind.LEVEL,
                hall);

        assertEquals("Hall", registered.id());
        assertEquals("levels/Hall.json", registered.path());
        assertEquals(AssetKind.LEVEL, registered.kind());
        assertSame(hall, registered.payload());
        assertSame(hall, registered.as(LevelDefinition.class));
        assertSame(registered, registry.find("Hall"));
        assertSame(registered, registry.find("levels/Hall.json"));
        assertTrue(registry.contains("Hall"));
        assertTrue(registry.contains("levels/Hall.json"));
        assertEquals(1, registry.size());
        assertSame(hall, registry.findLevel("Hall"));
        assertSame(hall, registry.findLevel("levels/Hall.json"));
    }

    @Test
    public void missingAssetReturnsNullAndRequireThrows() {
        assertNull(registry.find("Missing"));
        assertNull(registry.find(null));
        assertNull(registry.find(""));
        assertNull(registry.find("   "));
        assertFalse(registry.contains("Missing"));
        assertNull(registry.findLevel("Hall"));
        assertEquals(0, registry.size());
        try {
            registry.require("Missing");
            fail("expected unknown asset");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Missing"));
        }
    }

    @Test
    public void registerDemoAssetsIncludesHallLevelAndStubs() {
        registry.registerDemoAssets();

        assertTrue(registry.size() >= 5);
        LevelDefinition hall = registry.findLevel("Hall");
        assertNotNull(hall);
        assertEquals("Hall", hall.name());
        assertSame(hall, registry.findLevel(LevelDefinition.HALL_RESOURCE));
        assertEquals(AssetKind.LEVEL, registry.require("Hall").kind());

        Asset mesh = registry.require(AssetRegistry.HALL_MESH_ID);
        assertEquals(AssetKind.MESH, mesh.kind());
        assertSame(mesh, registry.find(AssetRegistry.HALL_MESH_PATH));
        assertNotNull(mesh.as(MeshHandle.class));

        Asset texture = registry.require(AssetRegistry.HALL_BEACON_TEXTURE_ID);
        assertEquals(AssetKind.TEXTURE, texture.kind());
        assertSame(texture, registry.find(AssetRegistry.HALL_BEACON_TEXTURE_PATH));
        assertNotNull(texture.as(TextureHandle.class));

        Asset audio = registry.require(AssetRegistry.HALL_AMBIENCE_ID);
        assertEquals(AssetKind.AUDIO, audio.kind());
        assertSame(audio, registry.find(AssetRegistry.HALL_AMBIENCE_PATH));
        assertNotNull(audio.as(AudioHandle.class));

        Asset mapping = registry.require(AssetRegistry.DEFAULT_MAPPING_ID);
        assertEquals(AssetKind.INPUT_MAPPING, mapping.kind());
        assertSame(mapping, registry.find(AssetRegistry.DEFAULT_MAPPING_PATH));
        assertNotNull(mapping.as(InputMappingContext.class));
    }

    @Test
    public void worldLoadLevelResolvesThroughRegistry() {
        World world = new World();
        world.assets().registerDemoAssets();

        Level hall = world.loadLevel("Hall");
        assertEquals("Hall", hall.name());
        assertEquals(2, world.actorCount());
        assertSame(hall, world.loadLevel(LevelDefinition.HALL_RESOURCE));
        assertEquals(2, world.actorCount());
    }

    @Test
    public void actorResolvesRegisteredTextureThroughWorldRegistry() {
        World world = new World(AssetRegistry.withDemoAssets());
        world.openLevel("Hall");

        HallBeaconActor beacon = world.actorsOf(HallBeaconActor.class).get(0);
        TextureHandle texture = beacon.texture();
        assertNotNull(texture);
        assertEquals(AssetRegistry.HALL_BEACON_TEXTURE_ID, texture.id());
        assertSame(
                texture,
                beacon.loadAsset(AssetRegistry.HALL_BEACON_TEXTURE_PATH, TextureHandle.class));
        assertSame(
                world.assets().find("Hall"),
                GameplayStatics.findAsset(world, "Hall"));
        assertSame(
                world.assets().require(LevelDefinition.HALL_RESOURCE),
                GameplayStatics.loadAsset(world, "Hall"));
    }

    @Test
    public void registerLevelOnWorldIndexesTheDefinition() {
        World world = new World();
        LevelDefinition side = LevelDefinition.named("Side")
                .actor(ActorTemplate.of("PlayerPawn").named("Pawn"));
        world.registerLevel(side);

        assertSame(side, world.assets().findLevel("Side"));
        assertSame(side, world.assets().findLevel("levels/Side.json"));
        assertEquals(1, world.loadLevel("levels/Side.json").actorCount());
        assertTrue(world.isLevelLoaded("levels/Side.json"));
        assertTrue(world.isLevelLoaded("Side"));
        assertEquals("Side", world.findLoadedLevel("levels/Side.json").name());
        assertTrue(world.unloadLevel("levels/Side.json"));
        assertFalse(world.isLevelLoaded("Side"));
        assertEquals(0, world.actorCount());
    }

    @Test
    public void registerRejectsIdOrPathOwnedByAnotherAsset() {
        LevelDefinition hall = LevelDefinition.named("Hall");
        registry.register("Hall", "levels/Hall.json", AssetKind.LEVEL, hall);

        try {
            registry.register("HallMesh", "Hall", AssetKind.MESH, new MeshHandle("HallMesh"));
            fail("expected path collision on Hall");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Hall"));
        }
        assertEquals(AssetKind.LEVEL, registry.require("Hall").kind());
        assertSame(hall, registry.findLevel("levels/Hall.json"));

        try {
            registry.register("levels/Hall.json", AssetKind.MESH, new MeshHandle("stolen"));
            fail("expected id collision on levels/Hall.json");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("levels/Hall.json"));
        }
        assertEquals(1, registry.size());
    }

    @Test
    public void unknownLevelStillThrowsAfterRegistryLookup() {
        World world = new World();
        try {
            world.loadLevel("Missing");
            fail("expected unknown level");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Missing"));
        }
    }

    @Test
    public void debugDumpListsRegisteredAssets() {
        World world = new World();
        world.assets().registerDemoAssets();
        world.openLevel("Hall");
        StringBuilder out = new StringBuilder();
        world.appendDump(out);
        String dump = out.toString();
        assertTrue(dump.contains("world.assets="));
        assertTrue(dump.contains("asset id=Hall path=levels/Hall.json kind=LEVEL"));
        assertTrue(dump.contains("kind=MESH"));
        assertTrue(dump.contains("kind=TEXTURE"));
        assertTrue(dump.contains("kind=AUDIO"));
        assertTrue(dump.contains("kind=INPUT_MAPPING"));
    }
}
