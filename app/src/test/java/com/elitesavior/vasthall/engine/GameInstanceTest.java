package com.elitesavior.vasthall.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

public final class GameInstanceTest {
    private GameInstance game;

    @Before
    public void setUp() {
        game = GameInstance.withDemoAssets();
        game.init();
        GameModeTypes.register("ProbeGameMode", ProbeGameMode.class);
        GameModeTypes.register("ArenaGameMode", ArenaGameMode.class);
        ActorTypes.register("ProbeActor", WorldTest.ProbeActor.class);
    }

    @Test
    public void initOwnsWorldConsoleAndAssets() {
        assertTrue(game.isInitialized());
        assertNotNull(game.world());
        assertNotNull(game.console());
        assertNotNull(game.assets());
        assertSame(game.world().assets(), game.assets());
        assertSame(game.world(), game.console().world());
        assertSame(game, game.world().gameInstance());
        assertSame(game.timerManager(), game.world().timerManager());
        assertSame(game.events(), game.world().events());
        assertSame(game.audio(), game.world().audio());
        assertEquals(4, game.events().listenerCount());
        assertEquals(4, game.assets().size());
        assertNull(game.gameMode());
    }

    @Test
    public void openLevelInstallsHallGameModeAndKeepsServices() {
        World world = game.world();
        DeveloperConsole console = game.console();
        AssetRegistry assets = game.assets();

        Level hall = game.openLevel("Hall");

        assertEquals("Hall", hall.name());
        assertTrue(world.isLevelLoaded("Hall"));
        assertSame(world, game.world());
        assertSame(console, game.console());
        assertSame(assets, game.assets());
        assertTrue(game.gameMode() instanceof HallGameMode);
        assertSame(game.gameMode(), world.gameMode());
        assertTrue(game.gameMode().hasStarted());
        assertSame(game, game.gameMode().gameInstance());
        assertSame(world, game.gameMode().world());
        assertEquals(PlayerPawn.class, game.gameMode().defaultPawnClass());
        assertNotNull(world.findActor(PlayerPawn.DEFAULT_NAME));
        assertEquals(1, world.actorsOf(PlayerPawn.class).size());
        assertEquals(2, world.actorCount());
    }

    @Test
    public void servicesSurviveFakeLevelTravelAndUnload() {
        World world = game.world();
        DeveloperConsole console = game.console();
        AssetRegistry assets = game.assets();
        console.exec("stat");

        game.openLevel("Hall");
        GameMode hallMode = game.gameMode();
        assertTrue(hallMode instanceof HallGameMode);
        assertEquals(1, ((HallGameMode) hallMode).initCount());
        assertEquals(1, ((HallGameMode) hallMode).startCount());

        world.registerLevel(sideDefinition());
        Level side = game.openLevel("Side");

        assertEquals("Side", side.name());
        assertSame(world, game.world());
        assertSame(console, game.console());
        assertSame(assets, game.assets());
        assertFalse(world.isLevelLoaded("Hall"));
        assertTrue(world.isLevelLoaded("Side"));
        assertNotSame(hallMode, game.gameMode());
        assertEquals(1, hallMode.endCount());
        assertTrue(game.gameMode() instanceof HallGameMode);
        assertTrue(console.log().get(0).startsWith("> stat"));
        assertEquals(4 + 1, assets.size());

        assertTrue(game.unloadLevel("Side"));
        assertFalse(world.isLevelLoaded("Side"));
        assertSame(world, game.world());
        assertSame(console, game.console());
        assertSame(assets, game.assets());
        assertNull(game.gameMode());
        assertNull(world.gameMode());
        assertEquals(1, hallMode.endCount());
    }

    @Test
    public void levelJsonSelectsGameModePerMap() {
        worldRegisterArena();
        game.openLevel("Arena");
        assertTrue(game.gameMode() instanceof ArenaGameMode);
        assertEquals(PlayerPawn.class, game.gameMode().defaultPawnClass());
    }

    @Test
    public void startPlaySpawnsDefaultPawnWhenLevelHasNone() {
        game.world().registerLevel(LevelDefinition.named("Empty").gameMode("HallGameMode"));
        game.openLevel("Empty");
        assertEquals(1, game.world().actorCount());
        Actor pawn = game.world().findActor(PlayerPawn.DEFAULT_NAME);
        assertNotNull(pawn);
        assertTrue(pawn instanceof PlayerPawn);
        assertEquals("Empty", pawn.levelName());
    }

    @Test
    public void defaultPawnDoesNotSurviveTravelToAMapThatPlacesOne() {
        game.world().registerLevel(LevelDefinition.named("Empty").gameMode("HallGameMode"));
        game.openLevel("Empty");
        assertEquals(1, game.world().actorsOf(PlayerPawn.class).size());

        game.openLevel("Hall");

        assertEquals(1, game.world().actorsOf(PlayerPawn.class).size());
        Actor pawn = game.world().findActor(PlayerPawn.DEFAULT_NAME);
        assertNotNull(pawn);
        assertEquals("Hall", pawn.levelName());
        assertEquals(2, game.world().actorCount());
    }

    @Test
    public void unknownLevelDoesNotTearDownCurrentSession() {
        game.openLevel("Hall");
        GameMode mode = game.gameMode();

        try {
            game.openLevel("MissingMap");
            fail("expected unknown level");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("unknown level"));
        }

        assertTrue(game.world().isLevelLoaded("Hall"));
        assertSame(mode, game.gameMode());
        assertTrue(mode.hasStarted());
        assertEquals(2, game.world().actorCount());
    }

    @Test
    public void gameplayStaticsOpenLevelGoesThroughGameInstance() {
        Level hall = GameplayStatics.openLevel(game, "Hall");
        assertEquals("Hall", hall.name());
        assertTrue(game.gameMode() instanceof HallGameMode);

        Level again = GameplayStatics.openLevel(game.world(), "Hall");
        assertEquals("Hall", again.name());
        assertTrue(game.gameMode() instanceof HallGameMode);
        assertSame(game, GameplayStatics.getGameInstance(game.world()));
        assertSame(game.gameMode(), GameplayStatics.getGameMode(game.world()));
    }

    @Test
    public void consoleOpenOnBoundWorldReinstallsGameMode() {
        DeveloperConsole console = game.console();
        String opened = console.exec("open Hall");
        assertTrue(opened.contains("opened Hall"));
        assertTrue(game.gameMode() instanceof HallGameMode);
        assertTrue(game.gameMode().hasStarted());

        String stat = console.exec("stat");
        assertTrue(stat.contains("mode=HallGameMode"));
        assertTrue(stat.contains("actors=2"));
    }

    @Test
    public void shutdownClearsWorldButKeepsOwnedServiceObjects() {
        game.openLevel("Hall");
        World world = game.world();
        DeveloperConsole console = game.console();
        game.shutdown();
        assertFalse(game.isInitialized());
        assertEquals(0, world.actorCount());
        assertTrue(world.loadedLevels().isEmpty());
        assertNull(game.gameMode());
        assertSame(world, game.world());
        assertSame(console, game.console());
    }

    @Test
    public void unknownGameModeClassThrows() {
        game.world().registerLevel(LevelDefinition.named("Bad").gameMode("MissingMode"));
        try {
            game.openLevel("Bad");
            fail("expected unknown game mode");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("MissingMode"));
        }
    }

    @Test
    public void debugDumpListsGameInstanceAndMode() {
        game.openLevel("Hall");
        StringBuilder out = new StringBuilder();
        game.world().appendDump(out);
        String dump = out.toString();
        assertTrue(dump.contains("game.instance=1"));
        assertTrue(dump.contains("game.saves=0"));
        assertTrue(dump.contains("game.mode=HallGameMode pawn=PlayerPawn started=1"));
        assertTrue(dump.contains("world.actors=2"));
        assertTrue(dump.contains("world.levels=1"));
        assertTrue(dump.contains("world.timers=1"));
        assertTrue(dump.contains("world.events=6"));
        assertTrue(dump.contains("world.audio=0"));
    }

    @Test
    public void hallJsonDeclaresHallGameMode() {
        LevelDefinition parsed = LevelDefinition.hall();
        assertEquals("HallGameMode", parsed.gameModeClassName());
        assertEquals("HallGameMode", GameModeTypes.resolve("HallGameMode").getSimpleName());
    }

    @Test
    public void probeGameModeHooksFireInOrder() {
        ProbeGameMode mode = new ProbeGameMode();
        game.setDefaultGameModeClass(ProbeGameMode.class);
        game.world().registerLevel(LevelDefinition.named("Hooked"));
        game.openLevel("Hooked", "listen=true");
        assertTrue(game.gameMode() instanceof ProbeGameMode);
        ProbeGameMode live = (ProbeGameMode) game.gameMode();
        assertNotSame(mode, live);
        assertEquals(1, live.initCount);
        assertEquals(1, live.startCount);
        assertEquals(0, live.endCount);
        assertEquals("listen=true", live.options());
        game.unloadLevel("Hooked");
        assertEquals(1, live.endCount);
        assertFalse(live.hasStarted());
    }

    private void worldRegisterArena() {
        game.world().registerLevel(LevelDefinition.named("Arena")
                .gameMode("ArenaGameMode")
                .actor(ActorTemplate.of("ProbeActor").named("ArenaLamp").at(1, 0, 0)));
    }

    private static LevelDefinition sideDefinition() {
        return LevelDefinition.named("Side")
                .actor(ActorTemplate.of("ProbeActor").named("SideLamp").at(2.0f, 0.0f, 1.0f));
    }

    public static final class ProbeGameMode extends GameMode {
        int initCount;
        int startCount;
        int endCount;

        @Override
        public void initGame(String options) {
            super.initGame(options);
            initCount++;
        }

        @Override
        public void startPlay() {
            super.startPlay();
            startCount++;
        }

        @Override
        public void endPlay() {
            super.endPlay();
            endCount++;
        }
    }

    public static final class ArenaGameMode extends GameMode {
        @Override
        public Class<? extends Actor> defaultPawnClass() {
            return PlayerPawn.class;
        }
    }
}
