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

import java.util.List;

public final class ComponentTest {
    private World world;

    @Before
    public void setUp() {
        world = new World();
    }

    @Test
    public void addComponentAttachesAndCallsOnAttach() {
        WorldTest.ProbeActor actor = world.spawnActor(WorldTest.ProbeActor.class);
        ProbeComponent component = actor.addComponent(new ProbeComponent());

        assertSame(actor, component.owner());
        assertTrue(component.isAttached());
        assertEquals(1, component.attachCount);
        assertEquals(0, component.detachCount);
        assertEquals(1, actor.componentCount());
        assertSame(component, actor.getComponent(ProbeComponent.class));
        assertSame(component, actor.components().get(0));
    }

    @Test
    public void removeComponentDetachesAndCallsOnDetach() {
        WorldTest.ProbeActor actor = world.spawnActor(WorldTest.ProbeActor.class);
        ProbeComponent component = actor.addComponent(new ProbeComponent());

        assertTrue(actor.removeComponent(component));

        assertEquals(1, component.detachCount);
        assertNull(component.owner());
        assertFalse(component.isAttached());
        assertEquals(0, actor.componentCount());
        assertNull(actor.getComponent(ProbeComponent.class));
        assertFalse(actor.removeComponent(component));
    }

    @Test
    public void destroyActorCleansUpComponents() {
        WorldTest.ProbeActor actor = world.spawnActor(WorldTest.ProbeActor.class);
        ProbeComponent first = actor.addComponent(new ProbeComponent());
        ProbeComponent second = actor.addComponent(new ProbeComponent());

        assertTrue(world.destroyActor(actor));

        assertEquals(1, first.detachCount);
        assertEquals(1, second.detachCount);
        assertNull(first.owner());
        assertNull(second.owner());
        assertFalse(first.isAttached());
        assertEquals(0, actor.componentCount());
        assertTrue(actor.components().isEmpty());
        assertEquals(1, actor.endPlayCount);
    }

    @Test
    public void tickForwardsToAttachedComponents() {
        WorldTest.ProbeActor actor = world.spawnActor(WorldTest.ProbeActor.class);
        ProbeComponent ticking = actor.addComponent(new ProbeComponent());
        ProbeComponent idle = actor.addComponent(new ProbeComponent());
        idle.setComponentTickEnabled(false);

        world.tick(0.016f);
        world.tick(0.016f);

        assertEquals(2, ticking.tickCount);
        assertEquals(0.016f, ticking.lastDt, 0.00001f);
        assertEquals(0, idle.tickCount);
        assertEquals(2, actor.tickCount);
    }

    @Test
    public void disabledActorTickSkipsComponentTick() {
        WorldTest.ProbeActor actor = world.spawnActor(WorldTest.ProbeActor.class);
        ProbeComponent component = actor.addComponent(new ProbeComponent());
        actor.setActorTickEnabled(false);

        world.tick(0.016f);

        assertEquals(0, actor.tickCount);
        assertEquals(0, component.tickCount);
    }

    @Test
    public void cannotAttachComponentAlreadyOwnedByAnotherActor() {
        WorldTest.ProbeActor first = world.spawnActor(WorldTest.ProbeActor.class);
        WorldTest.ProbeActor second = world.spawnActor(WorldTest.ProbeActor.class);
        ProbeComponent component = first.addComponent(new ProbeComponent());

        try {
            second.addComponent(component);
            fail("expected already attached");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("already"));
        }
        assertSame(first, component.owner());
        assertEquals(0, second.componentCount());
    }

    @Test
    public void componentsSnapshotDoesNotMutateOwner() {
        WorldTest.ProbeActor actor = world.spawnActor(WorldTest.ProbeActor.class);
        actor.addComponent(new ProbeComponent());
        List<ActorComponent> snapshot = actor.components();
        snapshot.clear();
        assertEquals(1, actor.componentCount());
    }

    @Test
    public void addAndRemoveDuringTickAreSafe() {
        WorldTest.ProbeActor host = world.spawnActor(WorldTest.ProbeActor.class);
        ProbeComponent doomed = host.addComponent(new ProbeComponent());
        host.onTick = (self, dt) -> {
            self.removeComponent(doomed);
            self.addComponent(new ProbeComponent());
        };

        world.tick(0.01f);

        assertEquals(1, doomed.detachCount);
        assertFalse(doomed.isAttached());
        assertEquals(1, host.componentCount());
        ProbeComponent added = host.getComponent(ProbeComponent.class);
        assertNotNull(added);
        assertEquals(0, added.tickCount);

        world.tick(0.01f);
        assertEquals(1, added.tickCount);
    }

    @Test
    public void movementComponentTranslatesOwnerOnTick() {
        Actor actor = world.spawnActor(Actor.class, Transform.at(1.0f, 2.0f, 3.0f));
        MovementComponent movement = actor.addComponent(new MovementComponent());
        movement.setVelocity(2.0f, -4.0f, 8.0f);

        world.tick(0.5f);

        assertEquals(2.0f, actor.transform().location.x, 0.0001f);
        assertEquals(0.0f, actor.transform().location.y, 0.0001f);
        assertEquals(7.0f, actor.transform().location.z, 0.0001f);
    }

    @Test
    public void tagComponentStoresAndQueriesTags() {
        Actor actor = world.spawnActor(Actor.class);
        TagComponent tags = actor.addComponent(new TagComponent("beacon"));

        assertTrue(tags.hasTag("beacon"));
        assertTrue(tags.addTag("demo"));
        assertTrue(tags.hasTag("demo"));
        assertFalse(tags.addTag("demo"));
        assertTrue(tags.removeTag("beacon"));
        assertFalse(tags.hasTag("beacon"));
        assertEquals(1, tags.tags().size());
        assertTrue(tags.tags().contains("demo"));
        assertFalse(tags.isComponentTickEnabled());
    }

    @Test
    public void hallActorsCarryExampleTagComponents() {
        PlayerPawn pawn = world.spawnActor(PlayerPawn.class);
        HallBeaconActor beacon = world.spawnActor(HallBeaconActor.class);

        assertTrue(pawn.getComponent(TagComponent.class).hasTag("pawn"));
        assertTrue(beacon.getComponent(TagComponent.class).hasTag("beacon"));
        assertEquals(1, pawn.componentCount());
        assertEquals(1, beacon.componentCount());
    }

    @Test
    public void unloadLevelDetachesComponentsOnOwnedActors() {
        ActorTypes.register("ProbeActor", WorldTest.ProbeActor.class);
        world.registerLevel(LevelDefinition.named("Hall")
                .actor(ActorTemplate.of("ProbeActor").named("Lamp")));
        world.loadLevel("Hall");
        WorldTest.ProbeActor lamp = (WorldTest.ProbeActor) world.findActor("Lamp");
        ProbeComponent component = lamp.addComponent(new ProbeComponent());

        assertTrue(world.unloadLevel("Hall"));

        assertEquals(1, component.detachCount);
        assertNull(component.owner());
        assertEquals(0, lamp.componentCount());
    }

    @Test
    public void debugDumpListsComponents() {
        HallBeaconActor beacon = world.spawnActor(
                HallBeaconActor.class, Transform.at(0.0f, 1.5f, 4.0f));
        MovementComponent movement = beacon.addComponent(new MovementComponent());
        movement.setVelocity(1.0f, 0.0f, 0.0f);
        movement.setComponentTickEnabled(false);

        StringBuilder out = new StringBuilder();
        world.appendDump(out);
        String dump = out.toString();
        assertTrue(dump.contains("components=2"));
        assertTrue(dump.contains("component class=TagComponent"));
        assertTrue(dump.contains("tags=beacon"));
        assertTrue(dump.contains("component class=MovementComponent"));
        assertTrue(dump.contains("vel=1.0000,0.0000,0.0000"));
    }

    public static final class ProbeComponent extends ActorComponent {
        int attachCount;
        int detachCount;
        int tickCount;
        float lastDt;

        @Override
        protected void onAttach() {
            attachCount++;
        }

        @Override
        protected void onDetach() {
            detachCount++;
        }

        @Override
        protected void tick(float deltaSeconds) {
            tickCount++;
            lastDt = deltaSeconds;
        }
    }
}
