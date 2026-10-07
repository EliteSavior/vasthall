package com.elitesavior.vasthall.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class GameplayEventsTest {
    @Test
    public void worldBroadcastsLevelAndActorHooks() {
        World world = new World(AssetRegistry.withDemoAssets());
        List<String> heard = new ArrayList<>();
        world.events().bind(EventType.LEVEL_LOADED, event -> heard.add("load:" + event.levelName()));
        world.events().bind(EventType.LEVEL_UNLOADED, event -> heard.add("unload:" + event.levelName()));
        world.events().bind(EventType.ACTOR_SPAWNED, event -> heard.add("spawn:" + event.actor().name()));
        world.events().bind(EventType.ACTOR_DESTROYED, event -> heard.add("destroy:" + event.actor().name()));

        world.loadLevel("Hall");
        assertTrue(heard.contains("load:Hall"));
        assertTrue(heard.contains("spawn:PlayerPawn"));
        assertTrue(heard.contains("spawn:HallBeacon"));

        world.unloadLevel("Hall");
        assertTrue(heard.contains("unload:Hall"));
        assertTrue(heard.contains("destroy:PlayerPawn"));
        assertTrue(heard.contains("destroy:HallBeacon"));

        int afterFirst = heard.size();
        world.loadLevel("Hall");
        world.loadLevel("Hall");
        assertEquals(2, countPrefix(heard, "load:Hall"));
        world.unloadLevel("Missing");
        assertEquals(afterFirst + 3, heard.size());
    }

    @Test
    public void unbindStopsWorldHooks() {
        World world = new World();
        List<String> heard = new ArrayList<>();
        DelegateHandle handle = world.events().bind(
                EventType.ACTOR_SPAWNED, event -> heard.add(event.actor().name()));

        Actor first = new Actor();
        first.setName("Keep");
        world.spawnActor(first, Transform.identity());
        world.events().unbind(handle);
        Actor second = new Actor();
        second.setName("Drop");
        world.spawnActor(second, Transform.identity());

        assertEquals(List.of("Keep"), heard);
        assertEquals(0, world.events().listenerCount(EventType.ACTOR_SPAWNED));
    }

    @Test
    public void spawnDuringTickBroadcastsAfterBeginPlay() {
        World world = new World();
        List<String> heard = new ArrayList<>();
        world.events().bind(EventType.ACTOR_SPAWNED, event -> {
            heard.add(event.actor().name());
            assertSame(world, event.actor().world());
            assertEquals(1, ((WorldTest.ProbeActor) event.actor()).beginPlayCount);
        });
        WorldTest.ProbeActor spawner = world.spawnActor(WorldTest.ProbeActor.class);
        spawner.setName("Spawner");
        heard.clear();
        spawner.onTick = (self, dt) -> {
            WorldTest.ProbeActor child = self.world().spawnActor(WorldTest.ProbeActor.class);
            child.setName("Child");
            self.onTick = null;
        };

        world.tick(0.016f);
        assertEquals(List.of("Child"), heard);
    }

    @Test
    public void templateNameIsAppliedBeforeActorSpawned() {
        World world = new World();
        world.registerLevel(LevelDefinition.named("Side")
                .actor(ActorTemplate.of("Actor").named("ArenaLamp")));
        List<String> heard = new ArrayList<>();
        world.events().bind(EventType.ACTOR_SPAWNED, event -> heard.add(event.actor().name()));

        world.loadLevel("Side");

        assertEquals(List.of("ArenaLamp"), heard);
    }

    @Test
    public void loadLevelDuringTickBroadcastsAfterActorsSpawn() {
        World world = new World();
        world.registerLevel(LevelDefinition.named("Side")
                .actor(ActorTemplate.of("Actor").named("ArenaLamp")));
        List<String> heard = new ArrayList<>();
        world.events().bind(EventType.ACTOR_SPAWNED, event -> heard.add("spawn:" + event.actor().name()));
        world.events().bind(EventType.LEVEL_LOADED, event -> heard.add("load:" + event.levelName()));
        WorldTest.ProbeActor spawner = world.spawnActor(WorldTest.ProbeActor.class);
        heard.clear();
        spawner.onTick = (self, dt) -> {
            self.world().loadLevel("Side");
            self.onTick = null;
        };

        world.tick(0.016f);

        assertEquals(List.of("spawn:ArenaLamp", "load:Side"), heard);
    }

    @Test
    public void unloadLevelDuringTickBroadcastsAfterActorsDestroy() {
        World world = new World();
        world.registerLevel(LevelDefinition.named("Side")
                .actor(ActorTemplate.of("Actor").named("ArenaLamp")));
        world.loadLevel("Side");
        List<String> heard = new ArrayList<>();
        world.events().bind(EventType.ACTOR_DESTROYED, event -> heard.add("destroy:" + event.actor().name()));
        world.events().bind(EventType.LEVEL_UNLOADED, event -> heard.add("unload:" + event.levelName()));
        WorldTest.ProbeActor watcher = world.spawnActor(WorldTest.ProbeActor.class);
        heard.clear();
        watcher.onTick = (self, dt) -> {
            self.world().unloadLevel("Side");
            self.onTick = null;
        };

        world.tick(0.016f);

        assertEquals(List.of("destroy:ArenaLamp", "unload:Side"), heard);
    }

    @Test
    public void gameInstanceExposesTheWorldEventDispatcher() {
        GameInstance game = GameInstance.withDemoAssets();
        game.init();
        assertNotNull(game.events());
        assertSame(game.world().events(), game.events());
        assertSame(game.events(), GameplayStatics.getEventDispatcher(game.world()));
        assertEquals(6, game.events().listenerCount());
    }

    @Test
    public void hallGameModeListensThenUnbindsOnTravel() {
        GameInstance game = GameInstance.withDemoAssets();
        game.init();
        game.openLevel("Hall");

        HallGameMode mode = (HallGameMode) game.gameMode();
        assertEquals(0, mode.actorSpawnedCount());
        assertEquals(0, mode.levelUnloadedCount());
        assertEquals(8, game.events().listenerCount());

        Actor extra = game.world().spawnActor(Actor.class);
        extra.setName("Extra");
        assertEquals(1, mode.actorSpawnedCount());

        game.world().registerLevel(LevelDefinition.named("Side"));
        game.loadLevel("Side");
        game.unloadLevel("Side");
        assertEquals(1, mode.levelUnloadedCount());
        assertSame(mode, game.gameMode());

        game.unloadLevel("Hall");
        assertEquals(2, mode.levelUnloadedCount());
        assertEquals(6, game.events().listenerCount());

        int heard = mode.actorSpawnedCount();
        Actor after = game.world().spawnActor(Actor.class);
        after.setName("After");
        assertEquals(heard, mode.actorSpawnedCount());
        assertFalse(game.events().isBound(new DelegateHandle()));
    }

    @Test
    public void consoleLogsEngineEventsAndListsListeners() {
        GameInstance game = GameInstance.withDemoAssets();
        game.init();
        DeveloperConsole console = game.console();

        game.openLevel("Hall");
        List<String> log = console.log();
        assertTrue(contains(log, "event LevelLoaded Hall"));
        assertTrue(contains(log, "event ActorSpawned PlayerPawn"));
        assertTrue(contains(log, "event ActorSpawned HallBeacon"));

        String listed = console.exec("events");
        assertTrue(listed.contains("events=8"));
        assertTrue(listed.contains("LevelLoaded listeners=1"));
        assertTrue(listed.contains("ActorSpawned listeners=2"));
        assertTrue(listed.contains("BeginOverlap listeners=1"));
        assertTrue(listed.contains("EndOverlap listeners=1"));

        String help = console.exec("help");
        assertTrue(help.contains("events"));
    }

    private static int countPrefix(List<String> heard, String value) {
        int count = 0;
        for (String line : heard) {
            if (value.equals(line)) {
                count++;
            }
        }
        return count;
    }

    private static boolean contains(List<String> log, String needle) {
        for (String line : log) {
            if (line.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
