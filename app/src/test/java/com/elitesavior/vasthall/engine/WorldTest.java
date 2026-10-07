package com.elitesavior.vasthall.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class WorldTest {
    private World world;

    @Before
    public void setUp() {
        world = new World();
    }

    @Test
    public void spawnActorOwnsActorAndCallsBeginPlay() {
        ProbeActor actor = world.spawnActor(ProbeActor.class, Transform.at(1.0f, 2.0f, 3.0f));
        assertNotNull(actor);
        assertSame(world, actor.world());
        assertTrue(actor.id() > 0);
        assertEquals(1, actor.beginPlayCount);
        assertEquals(0, actor.endPlayCount);
        assertEquals(1, world.actorCount());
        assertEquals(1.0f, actor.transform().location.x, 0.0001f);
        assertEquals(2.0f, actor.transform().location.y, 0.0001f);
        assertEquals(3.0f, actor.transform().location.z, 0.0001f);
        assertEquals(1.0f, actor.transform().scale.x, 0.0001f);
    }

    @Test
    public void destroyActorRemovesFromWorldAndCallsEndPlay() {
        ProbeActor actor = world.spawnActor(ProbeActor.class);
        assertTrue(world.destroyActor(actor));
        assertEquals(1, actor.endPlayCount);
        assertTrue(actor.isPendingKill());
        assertNull(actor.world());
        assertEquals(0, world.actorCount());
        assertTrue(world.actors().isEmpty());
        assertFalse(world.destroyActor(actor));
    }

    @Test
    public void tickInvokesOnlyRegisteredLiveActors() {
        ProbeActor ticking = world.spawnActor(ProbeActor.class);
        ProbeActor idle = world.spawnActor(ProbeActor.class);
        idle.setActorTickEnabled(false);

        world.tick(0.016f);
        world.tick(0.016f);

        assertEquals(2, ticking.tickCount);
        assertEquals(0.016f, ticking.lastDt, 0.00001f);
        assertEquals(0, idle.tickCount);
        assertEquals(2, world.frameCount());
    }

    @Test
    public void actorsCanBeQueriedByNameAndClass() {
        ProbeActor first = world.spawnActor(ProbeActor.class);
        first.setName("Alpha");
        HallBeaconActor beacon = world.spawnActor(
                HallBeaconActor.class, Transform.at(0.0f, 1.5f, 4.0f));
        PlayerPawn pawn = world.spawnActor(PlayerPawn.class);

        assertEquals(3, world.actorCount());
        assertSame(first, world.findActor("Alpha"));
        assertSame(beacon, world.findActor(HallBeaconActor.DEFAULT_NAME));
        assertSame(pawn, world.findActor(PlayerPawn.DEFAULT_NAME));
        assertEquals(1, world.actorsOf(HallBeaconActor.class).size());
        assertEquals(1, world.actorsOf(PlayerPawn.class).size());
        List<Actor> snapshot = world.actors();
        assertEquals(3, snapshot.size());
        snapshot.clear();
        assertEquals(3, world.actorCount());
    }

    @Test
    public void spawnAndDestroyDuringTickAreDeferredUntilAfterTick() {
        ProbeActor host = world.spawnActor(ProbeActor.class);
        host.onTick = (self, dt) -> {
            ProbeActor child = self.world().spawnActor(ProbeActor.class);
            child.setName("Child");
            self.world().destroyActor(self);
        };

        world.tick(0.01f);

        assertEquals(1, host.endPlayCount);
        assertEquals(1, world.actorCount());
        Actor child = world.findActor("Child");
        assertNotNull(child);
        assertEquals(1, ((ProbeActor) child).beginPlayCount);
        assertEquals(0, ((ProbeActor) child).tickCount);

        world.tick(0.01f);
        assertEquals(1, ((ProbeActor) child).tickCount);
    }

    @Test
    public void hallBeaconBobsAndYawsOnTick() {
        HallBeaconActor beacon = world.spawnActor(
                HallBeaconActor.class, Transform.at(0.0f, 1.5f, 4.0f));
        float y0 = beacon.transform().location.y;
        float yaw0 = beacon.transform().rotation.yaw;
        world.tick(0.25f);
        assertTrue(beacon.transform().location.y != y0);
        assertTrue(beacon.transform().rotation.yaw != yaw0);
        assertEquals(0.0f, beacon.transform().location.x, 0.0001f);
        assertEquals(4.0f, beacon.transform().location.z, 0.0001f);
    }

    @Test
    public void playerPawnIsSpawnedWithoutTickByDefault() {
        PlayerPawn pawn = world.spawnActor(PlayerPawn.class, Transform.identity());
        world.tick(1.0f);
        assertEquals(PlayerPawn.DEFAULT_NAME, pawn.name());
        assertFalse(pawn.isActorTickEnabled());
        assertEquals(0.0f, pawn.transform().location.x, 0.0001f);
        assertEquals(1.0f, pawn.transform().scale.z, 0.0001f);
    }

    @Test
    public void destroyAllClearsTheWorld() {
        world.spawnActor(ProbeActor.class);
        world.spawnActor(HallBeaconActor.class);
        world.destroyAll();
        assertEquals(0, world.actorCount());
        assertTrue(world.actors().isEmpty());
    }

    @Test
    public void debugDumpListsActors() {
        world.spawnActor(PlayerPawn.class);
        HallBeaconActor beacon = world.spawnActor(
                HallBeaconActor.class, Transform.at(0.0f, 1.5f, 4.0f));
        beacon.setActorTickEnabled(true);
        StringBuilder out = new StringBuilder();
        world.appendDump(out);
        String dump = out.toString();
        assertTrue(dump.contains("world.actors=2"));
        assertTrue(dump.contains("name=PlayerPawn"));
        assertTrue(dump.contains("name=HallBeacon"));
        assertTrue(dump.contains("class=HallBeaconActor"));
        assertTrue(dump.contains("loc=0.0000,1.5000,4.0000"));
    }

    public static final class ProbeActor extends Actor {
        int beginPlayCount;
        int endPlayCount;
        int tickCount;
        float lastDt;
        TickHook onTick;

        interface TickHook {
            void run(ProbeActor self, float dt);
        }

        @Override
        protected void beginPlay() {
            beginPlayCount++;
        }

        @Override
        protected void endPlay() {
            endPlayCount++;
        }

        @Override
        protected void tick(float deltaSeconds) {
            tickCount++;
            lastDt = deltaSeconds;
            if (onTick != null) {
                onTick.run(this, deltaSeconds);
            }
        }
    }
}
