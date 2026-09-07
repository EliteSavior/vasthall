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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public final class LevelTest {
    private World world;

    @Before
    public void setUp() {
        world = new World();
        ActorTypes.register("ProbeActor", WorldTest.ProbeActor.class);
    }

    @Test
    public void loadLevelSpawnsActorsFromDefinition() {
        world.registerLevel(hallDefinition());

        Level hall = world.loadLevel("Hall");

        assertNotNull(hall);
        assertEquals("Hall", hall.name());
        assertTrue(world.isLevelLoaded("Hall"));
        assertSame(hall, world.findLoadedLevel("Hall"));
        assertEquals(2, hall.actorCount());
        assertEquals(2, world.actorCount());
        Actor pawn = world.findActor(PlayerPawn.DEFAULT_NAME);
        Actor beacon = world.findActor(HallBeaconActor.DEFAULT_NAME);
        assertNotNull(pawn);
        assertNotNull(beacon);
        assertEquals("Hall", pawn.levelName());
        assertEquals("Hall", beacon.levelName());
        assertEquals(0.0f, pawn.transform().location.x, 0.0001f);
        assertEquals(1.5f, beacon.transform().location.y, 0.0001f);
        assertEquals(4.0f, beacon.transform().location.z, 0.0001f);
        assertFalse(pawn.isActorTickEnabled());
        assertTrue(beacon.isActorTickEnabled());
        assertEquals(1, world.actorsOf(HallBeaconActor.class).size());
    }

    @Test
    public void unloadLevelDestroysOwnedActorsAndClearsRegistry() {
        world.registerLevel(hallDefinition());
        Level hall = world.loadLevel("Hall");
        Actor pawn = world.findActor(PlayerPawn.DEFAULT_NAME);
        Actor beacon = world.findActor(HallBeaconActor.DEFAULT_NAME);
        WorldTest.ProbeActor persistent = world.spawnActor(WorldTest.ProbeActor.class);
        persistent.setName("Persistent");

        assertTrue(world.unloadLevel("Hall"));

        assertFalse(world.isLevelLoaded("Hall"));
        assertNull(world.findLoadedLevel("Hall"));
        assertNull(world.findActor(PlayerPawn.DEFAULT_NAME));
        assertNull(world.findActor(HallBeaconActor.DEFAULT_NAME));
        assertTrue(pawn.isPendingKill());
        assertTrue(beacon.isPendingKill());
        assertNull(pawn.world());
        assertNull(beacon.world());
        assertEquals(1, world.actorCount());
        assertSame(persistent, world.findActor("Persistent"));
        assertEquals(0, hall.actorCount());
        assertFalse(world.unloadLevel("Hall"));
    }

    @Test
    public void unloadLevelDoesNotTouchActorsFromAnotherLevel() {
        world.registerLevel(hallDefinition());
        world.registerLevel(sideDefinition());
        world.loadLevel("Hall");
        world.loadLevel("Side");

        assertEquals(3, world.actorCount());
        assertTrue(world.unloadLevel("Side"));

        assertFalse(world.isLevelLoaded("Side"));
        assertTrue(world.isLevelLoaded("Hall"));
        assertEquals(2, world.actorCount());
        assertNotNull(world.findActor(PlayerPawn.DEFAULT_NAME));
        assertNotNull(world.findActor(HallBeaconActor.DEFAULT_NAME));
        assertNull(world.findActor("SideLamp"));
    }

    @Test
    public void loadLevelIsIdempotentAndDoesNotDuplicateActors() {
        world.registerLevel(hallDefinition());
        Level first = world.loadLevel("Hall");
        Level second = world.loadLevel("Hall");

        assertSame(first, second);
        assertEquals(2, world.actorCount());
        assertEquals(1, world.actorsOf(PlayerPawn.class).size());
        assertEquals(1, world.actorsOf(HallBeaconActor.class).size());
    }

    @Test
    public void openLevelUnloadsPreviousThenLoadsNamedLevel() {
        world.registerLevel(hallDefinition());
        world.registerLevel(sideDefinition());
        world.loadLevel("Hall");
        WorldTest.ProbeActor persistent = world.spawnActor(WorldTest.ProbeActor.class);
        persistent.setName("Persistent");

        Level side = world.openLevel("Side");

        assertEquals("Side", side.name());
        assertFalse(world.isLevelLoaded("Hall"));
        assertTrue(world.isLevelLoaded("Side"));
        assertEquals(2, world.actorCount());
        assertNull(world.findActor(HallBeaconActor.DEFAULT_NAME));
        assertNotNull(world.findActor("SideLamp"));
        assertSame(persistent, world.findActor("Persistent"));
    }

    @Test
    public void gameplayStaticsMirrorsUnrealLoadUnloadOpenNames() {
        world.registerLevel(hallDefinition());
        world.registerLevel(sideDefinition());

        Level hall = GameplayStatics.loadLevel(world, "Hall");
        assertEquals("Hall", hall.name());
        assertTrue(GameplayStatics.unloadLevel(world, "Hall"));
        assertFalse(world.isLevelLoaded("Hall"));

        Level opened = GameplayStatics.openLevel(world, "Side");
        assertEquals("Side", opened.name());
        assertTrue(world.isLevelLoaded("Side"));
    }

    @Test
    public void unknownLevelThrows() {
        try {
            world.loadLevel("Missing");
            fail("expected unknown level");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Missing"));
        }
    }

    @Test
    public void destroyingOwnedActorRemovesItFromLevelWithoutLeaking() {
        world.registerLevel(hallDefinition());
        Level hall = world.loadLevel("Hall");
        Actor beacon = world.findActor(HallBeaconActor.DEFAULT_NAME);

        assertTrue(world.destroyActor(beacon));

        assertEquals(1, hall.actorCount());
        assertEquals(1, world.actorCount());
        assertFalse(hall.contains(beacon));
        assertTrue(world.isLevelLoaded("Hall"));
        assertNull(world.findActor(HallBeaconActor.DEFAULT_NAME));
        assertNotNull(world.findActor(PlayerPawn.DEFAULT_NAME));
    }

    @Test
    public void hallJsonResourceParsesAndLoadsTheDemoLevel() {
        LevelDefinition parsed = LevelJson.parse(readClasspath("levels/Hall.json"));
        assertEquals("Hall", parsed.name());
        assertEquals(2, parsed.actors().size());
        assertEquals("PlayerPawn", parsed.actors().get(0).className());
        assertEquals("HallBeaconActor", parsed.actors().get(1).className());
        assertEquals(1.5f, parsed.actors().get(1).transform().location.y, 0.0001f);
        assertEquals(4.0f, parsed.actors().get(1).transform().location.z, 0.0001f);

        world.registerLevel(parsed);
        world.loadLevel("Hall");
        assertEquals(2, world.actorCount());
        assertNotNull(world.findActor("PlayerPawn"));
        assertNotNull(world.findActor("HallBeacon"));
    }

    @Test
    public void builtInHallMatchesClasspathSample() {
        LevelDefinition builtIn = LevelDefinition.hall();
        world.registerLevel(builtIn);
        world.loadLevel("Hall");
        assertEquals(2, world.actorCount());
        HallBeaconActor beacon = world.actorsOf(HallBeaconActor.class).get(0);
        assertEquals(HallBeaconActor.BASE_Y, beacon.transform().location.y, 0.0001f);
    }

    @Test
    public void debugDumpListsLoadedLevels() {
        world.registerLevel(hallDefinition());
        world.loadLevel("Hall");
        StringBuilder out = new StringBuilder();
        world.appendDump(out);
        String dump = out.toString();
        assertTrue(dump.contains("world.levels=1"));
        assertTrue(dump.contains("level=Hall actors=2"));
        assertTrue(dump.contains("name=PlayerPawn"));
        assertTrue(dump.contains("name=HallBeacon"));
    }

    @Test
    public void spawnDuringTickFromLoadedLevelStillOwnsActors() {
        world.registerLevel(sideDefinition());
        WorldTest.ProbeActor host = world.spawnActor(WorldTest.ProbeActor.class);
        host.onTick = (self, dt) -> self.world().loadLevel("Side");

        world.tick(0.01f);

        assertTrue(world.isLevelLoaded("Side"));
        Actor lamp = world.findActor("SideLamp");
        assertNotNull(lamp);
        assertEquals("Side", lamp.levelName());
        assertEquals(1, ((WorldTest.ProbeActor) lamp).beginPlayCount);
    }

    private static LevelDefinition hallDefinition() {
        return LevelDefinition.named("Hall")
                .actor(ActorTemplate.of("PlayerPawn")
                        .named("PlayerPawn")
                        .at(0.0f, 0.0f, 0.0f)
                        .tickEnabled(false))
                .actor(ActorTemplate.of("HallBeaconActor")
                        .named("HallBeacon")
                        .at(0.0f, 1.5f, 4.0f)
                        .tickEnabled(true));
    }

    private static LevelDefinition sideDefinition() {
        return LevelDefinition.named("Side")
                .actor(ActorTemplate.of("ProbeActor")
                        .named("SideLamp")
                        .at(2.0f, 0.0f, 1.0f));
    }

    private static String readClasspath(String path) {
        InputStream stream = LevelTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull("missing classpath resource: " + path, stream);
        try (Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8.name())) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
}
