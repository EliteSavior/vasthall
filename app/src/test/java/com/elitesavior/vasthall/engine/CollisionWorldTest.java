package com.elitesavior.vasthall.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class CollisionWorldTest {
    private World world;
    private List<String> begins;
    private List<String> ends;

    @Before
    public void setUp() {
        world = new World();
        begins = new ArrayList<>();
        ends = new ArrayList<>();
        world.events().bind(EventType.BEGIN_OVERLAP, event -> begins.add(label(event)));
        world.events().bind(EventType.END_OVERLAP, event -> ends.add(label(event)));
    }

    @Test
    public void overlappingBoxesBroadcastBeginThenEnd() {
        Actor left = spawnBox("Left", Transform.at(0.0f, 0.0f, 0.0f), 0.5f, 0.5f, 0.5f);
        Actor right = spawnBox("Right", Transform.at(2.0f, 0.0f, 0.0f), 0.5f, 0.5f, 0.5f);

        world.tick(0.016f);
        assertEquals(0, begins.size());
        assertEquals(0, world.collision().overlapCount());
        assertTrue(world.collision().queryOverlaps(left.getComponent(CollisionComponent.class)).isEmpty());

        right.transform().location.x = 0.6f;
        world.tick(0.016f);

        assertEquals(List.of("Left|Right"), begins);
        assertTrue(ends.isEmpty());
        assertEquals(1, world.collision().overlapCount());
        assertTrue(world.collision().isOverlapping(
                left.getComponent(CollisionComponent.class),
                right.getComponent(CollisionComponent.class)));
        List<CollisionComponent> hits = world.collision()
                .queryOverlaps(left.getComponent(CollisionComponent.class));
        assertEquals(1, hits.size());
        assertSame(right.getComponent(CollisionComponent.class), hits.get(0));

        right.transform().location.x = 3.0f;
        world.tick(0.016f);

        assertEquals(List.of("Left|Right"), ends);
        assertEquals(0, world.collision().overlapCount());
        assertFalse(world.collision().isOverlapping(
                left.getComponent(CollisionComponent.class),
                right.getComponent(CollisionComponent.class)));
    }

    @Test
    public void touchingEdgesCountAsEnter() {
        spawnBox("A", Transform.at(0.0f, 0.0f, 0.0f), 0.5f, 0.5f, 0.5f);
        spawnBox("B", Transform.at(1.0f, 0.0f, 0.0f), 0.5f, 0.5f, 0.5f);

        world.tick(0.016f);

        assertEquals(List.of("A|B"), begins);
        assertTrue(ends.isEmpty());
    }

    @Test
    public void destroyOverlappingActorBroadcastsEndOverlap() {
        Actor left = spawnBox("Keep", Transform.at(0.0f, 0.0f, 0.0f), 0.5f, 0.5f, 0.5f);
        Actor doomed = spawnBox("Doomed", Transform.at(0.4f, 0.0f, 0.0f), 0.5f, 0.5f, 0.5f);
        world.tick(0.016f);
        assertEquals(1, begins.size());

        assertTrue(world.destroyActor(doomed));

        assertEquals(List.of("Doomed|Keep"), ends);
        assertEquals(0, world.collision().overlapCount());
        assertTrue(world.collision().queryOverlaps(left.getComponent(CollisionComponent.class)).isEmpty());
    }

    @Test
    public void spawnDuringTickBeginsAfterBeginPlay() {
        Actor left = spawnBox("Anchor", Transform.at(0.0f, 0.0f, 0.0f), 0.5f, 0.5f, 0.5f);
        WorldTest.ProbeActor spawner = world.spawnActor(WorldTest.ProbeActor.class);
        spawner.onTick = (self, dt) -> {
            spawnBox("Child", Transform.at(0.2f, 0.0f, 0.0f), 0.5f, 0.5f, 0.5f);
            self.onTick = null;
        };
        world.events().bind(EventType.BEGIN_OVERLAP, event -> {
            Actor child = "Child".equals(event.actor().name()) ? event.actor() : event.otherActor();
            if (child instanceof WorldTest.ProbeActor probe) {
                assertEquals(1, probe.beginPlayCount);
            }
        });

        world.tick(0.016f);

        assertEquals(List.of("Anchor|Child"), begins);
        assertSame(world, left.world());
    }

    @Test
    public void sphereOverlapsBoxAndOtherSphere() {
        Actor boxActor = world.spawnActor(Actor.class, Transform.at(0.0f, 0.0f, 0.0f));
        boxActor.setName("Box");
        boxActor.addComponent(new CollisionComponent().setBoxExtent(1.0f, 1.0f, 1.0f));

        Actor sphereActor = world.spawnActor(Actor.class, Transform.at(1.6f, 0.0f, 0.0f));
        sphereActor.setName("Ball");
        sphereActor.addComponent(new CollisionComponent().setSphereRadius(0.7f));

        world.tick(0.016f);
        assertEquals(List.of("Ball|Box"), begins);

        Actor other = world.spawnActor(Actor.class, Transform.at(2.2f, 0.0f, 0.0f));
        other.setName("Orb");
        other.addComponent(new CollisionComponent().setSphereRadius(0.7f));
        world.tick(0.016f);
        assertTrue(begins.contains("Ball|Orb"));
    }

    @Test
    public void sameActorComponentsDoNotPair() {
        Actor actor = world.spawnActor(Actor.class, Transform.identity());
        actor.setName("Stack");
        actor.addComponent(new CollisionComponent().setBoxExtent(1.0f, 1.0f, 1.0f));
        actor.addComponent(new CollisionComponent().setBoxExtent(1.0f, 1.0f, 1.0f));

        world.tick(0.016f);

        assertTrue(begins.isEmpty());
        assertEquals(0, world.collision().overlapCount());
    }

    @Test
    public void generateOverlapEventsFalseStillQueries() {
        Actor left = spawnBox("Quiet", Transform.at(0.0f, 0.0f, 0.0f), 0.5f, 0.5f, 0.5f);
        left.getComponent(CollisionComponent.class).setGenerateOverlapEvents(false);
        Actor right = spawnBox("Loud", Transform.at(0.2f, 0.0f, 0.0f), 0.5f, 0.5f, 0.5f);

        world.tick(0.016f);

        assertTrue(begins.isEmpty());
        assertEquals(0, world.collision().overlapCount());
        assertEquals(1, world.collision()
                .queryOverlaps(right.getComponent(CollisionComponent.class)).size());
    }

    @Test
    public void collisionDisabledDropsPair() {
        Actor left = spawnBox("On", Transform.at(0.0f, 0.0f, 0.0f), 0.5f, 0.5f, 0.5f);
        Actor right = spawnBox("Off", Transform.at(0.2f, 0.0f, 0.0f), 0.5f, 0.5f, 0.5f);
        world.tick(0.016f);
        assertEquals(1, begins.size());

        right.getComponent(CollisionComponent.class).setCollisionEnabled(false);
        world.tick(0.016f);

        assertEquals(1, ends.size());
        assertEquals(0, world.collision().overlapCount());
    }

    @Test
    public void actorScaleGrowsWorldBounds() {
        Actor left = spawnBox("Tiny", Transform.at(0.0f, 0.0f, 0.0f), 0.25f, 0.25f, 0.25f);
        Actor right = spawnBox("Scaled", Transform.at(1.2f, 0.0f, 0.0f), 0.25f, 0.25f, 0.25f);
        world.tick(0.016f);
        assertTrue(begins.isEmpty());

        right.transform().scale.set(4.0f, 1.0f, 1.0f);
        world.tick(0.016f);
        assertEquals(List.of("Scaled|Tiny"), begins);
        assertEquals(2.2f, right.getComponent(CollisionComponent.class).worldBounds().max.x, 0.0001f);
        assertNotNull(left.getComponent(CollisionComponent.class).worldBounds());
    }

    @Test
    public void gameplayStaticsReachCollisionThroughWorld() {
        GameInstance game = GameInstance.withDemoAssets();
        game.init();
        assertSame(game.world().collision(), GameplayStatics.getCollisionWorld(game.world()));
        assertSame(game.collision(), game.world().collision());

        Actor a = spawnBox("A", Transform.at(0.0f, 0.0f, 0.0f), 0.5f, 0.5f, 0.5f);
        Actor b = spawnBox("B", Transform.at(0.2f, 0.0f, 0.0f), 0.5f, 0.5f, 0.5f);
        world.tick(0.016f);
        assertEquals(1, GameplayStatics.overlapCount(world));
        assertEquals(1, GameplayStatics.queryOverlaps(
                world, a.getComponent(CollisionComponent.class)).size());
        assertTrue(GameplayStatics.isOverlapping(
                world,
                a.getComponent(CollisionComponent.class),
                b.getComponent(CollisionComponent.class)));
    }

    @Test
    public void hallPawnAndBeaconCarryBoxesWithoutOverlapping() {
        GameInstance game = GameInstance.withDemoAssets();
        game.init();
        game.openLevel("Hall");
        game.world().tick(0.016f);

        PlayerPawn pawn = game.world().actorsOf(PlayerPawn.class).get(0);
        HallBeaconActor beacon = game.world().actorsOf(HallBeaconActor.class).get(0);
        assertNotNull(pawn.getComponent(CollisionComponent.class));
        assertNotNull(beacon.getComponent(CollisionComponent.class));
        assertEquals(0, game.collision().overlapCount());
    }

    @Test
    public void consoleListsOverlapsAndDebugDrawStub() {
        GameInstance game = GameInstance.withDemoAssets();
        game.init();
        DeveloperConsole console = game.console();
        Actor a = game.world().spawnActor(Actor.class, Transform.at(0.0f, 0.0f, 0.0f));
        a.setName("Alpha");
        a.addComponent(new CollisionComponent().setBoxExtent(0.5f, 0.5f, 0.5f));
        Actor b = game.world().spawnActor(Actor.class, Transform.at(0.2f, 0.0f, 0.0f));
        b.setName("Beta");
        b.addComponent(new CollisionComponent().setBoxExtent(0.5f, 0.5f, 0.5f));
        game.world().tick(0.016f);

        String listed = console.exec("ListOverlaps");
        assertTrue(listed.contains("overlaps=1"));
        assertTrue(listed.contains("Alpha"));
        assertTrue(listed.contains("Beta"));
        assertTrue(listed.contains("debugDraw=0"));

        String flag = console.exec("DebugDrawOverlaps 1");
        assertTrue(flag.contains("debugDraw=1"));
        assertTrue(game.collision().debugDraw());
        assertTrue(console.exec("listoverlaps").contains("debugDraw=1"));
        assertTrue(console.exec("debugdrawoverlaps 0").contains("debugDraw=0"));
        assertFalse(game.collision().debugDraw());

        String help = console.exec("help");
        assertTrue(help.contains("listoverlaps"));
        assertTrue(help.contains("debugdrawoverlaps"));

        StringBuilder out = new StringBuilder();
        game.world().appendDump(out);
        assertTrue(out.toString().contains("world.overlaps=1"));
        String stat = console.exec("stat");
        assertTrue(stat.contains("overlaps=1"));
    }

    @Test
    public void worldDestroyAllClearsOverlapPairs() {
        spawnBox("A", Transform.at(0.0f, 0.0f, 0.0f), 0.5f, 0.5f, 0.5f);
        spawnBox("B", Transform.at(0.2f, 0.0f, 0.0f), 0.5f, 0.5f, 0.5f);
        world.tick(0.016f);
        assertEquals(1, world.collision().overlapCount());

        world.destroyAll();

        assertEquals(1, ends.size());
        assertEquals(0, world.collision().overlapCount());
    }

    private Actor spawnBox(String name, Transform at, float hx, float hy, float hz) {
        Actor actor;
        if ("Child".equals(name)) {
            actor = world.spawnActor(WorldTest.ProbeActor.class, at);
        } else {
            actor = world.spawnActor(Actor.class, at);
        }
        actor.setName(name);
        actor.addComponent(new CollisionComponent().setBoxExtent(hx, hy, hz));
        return actor;
    }

    private static String label(OverlapEvent event) {
        String first = event.actor().name();
        String second = event.otherActor().name();
        if (first.compareTo(second) <= 0) {
            return first + "|" + second;
        }
        return second + "|" + first;
    }
}
