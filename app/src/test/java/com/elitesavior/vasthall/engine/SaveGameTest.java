package com.elitesavior.vasthall.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public final class SaveGameTest {
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private GameInstance game;
    private File saveDir;

    @Before
    public void setUp() throws Exception {
        saveDir = temp.newFolder("saves");
        game = GameInstance.withDemoAssets();
        game.setSaveDirectory(saveDir);
        game.init();
        game.openLevel("Hall");
    }

    @Test
    public void createSaveGameCapturesLevelAndActorState() {
        Actor beacon = game.world().findActor("HallBeacon");
        assertNotNull(beacon);
        beacon.transform().location.set(1.5f, 2.25f, 3.0f);
        beacon.transform().rotation.set(10.0f, 45.0f, 0.0f);
        beacon.transform().scale.set(1.0f, 2.0f, 1.0f);

        SaveGame save = game.createSaveGame();

        assertEquals(SaveGame.FORMAT_VERSION, save.version());
        assertEquals("Hall", save.levelName());
        assertEquals("HallGameMode", save.gameMode());
        assertEquals(2, save.actors().size());

        SaveGame.ActorRecord pawn = save.findActor("PlayerPawn");
        assertNotNull(pawn);
        assertEquals("PlayerPawn", pawn.className());
        assertEquals("Hall", pawn.levelName());
        assertEquals(0.0f, pawn.location().x, 0.0001f);
        assertFalse(pawn.tickEnabled());
        assertTrue(pawn.tags().contains("pawn"));

        SaveGame.ActorRecord recorded = save.findActor("HallBeacon");
        assertNotNull(recorded);
        assertEquals("HallBeaconActor", recorded.className());
        assertEquals(1.5f, recorded.location().x, 0.0001f);
        assertEquals(2.25f, recorded.location().y, 0.0001f);
        assertEquals(3.0f, recorded.location().z, 0.0001f);
        assertEquals(10.0f, recorded.rotation().pitch, 0.0001f);
        assertEquals(45.0f, recorded.rotation().yaw, 0.0001f);
        assertEquals(2.0f, recorded.scale().y, 0.0001f);
        assertTrue(recorded.tickEnabled());
        assertTrue(recorded.tags().contains("beacon"));
    }

    @Test
    public void saveLoadRoundTripThroughTempDirectory() {
        Actor beacon = game.world().findActor("HallBeacon");
        beacon.transform().location.set(0.5f, 3.0f, 8.0f);
        beacon.transform().rotation.yaw = 90.0f;

        assertFalse(game.doesSaveGameExist("Slot0"));
        assertTrue(game.saveGameToSlot("Slot0"));
        assertTrue(game.doesSaveGameExist("Slot0"));
        assertTrue(new File(saveDir, "Slot0.sav").isFile());
        assertEquals(1, game.saveSlots().size());
        assertTrue(game.saveSlots().contains("Slot0"));

        beacon.transform().location.set(0.0f, 1.5f, 4.0f);
        beacon.transform().rotation.yaw = 0.0f;

        SaveGame loaded = game.loadSaveGameObject("Slot0");
        assertNotNull(loaded);
        assertEquals("Hall", loaded.levelName());
        SaveGame.ActorRecord recorded = loaded.findActor("HallBeacon");
        assertEquals(0.5f, recorded.location().x, 0.0001f);
        assertEquals(3.0f, recorded.location().y, 0.0001f);
        assertEquals(8.0f, recorded.location().z, 0.0001f);
        assertEquals(90.0f, recorded.rotation().yaw, 0.0001f);
    }

    @Test
    public void loadGameRestoresWorldAfterMutationAndTravel() {
        Actor beacon = game.world().findActor("HallBeacon");
        beacon.transform().location.set(4.0f, 6.0f, 2.0f);
        beacon.transform().rotation.yaw = 30.0f;
        TagComponent tags = beacon.getComponent(TagComponent.class);
        tags.addTag("saved");

        assertTrue(GameplayStatics.saveGameToSlot(game, "Travel"));

        game.world().registerLevel(LevelDefinition.named("Empty").gameMode("HallGameMode"));
        game.openLevel("Empty");
        assertNull(game.world().findActor("HallBeacon"));
        assertEquals("Empty", game.world().loadedLevels().get(0).name());

        assertTrue(GameplayStatics.loadGameFromSlot(game, "Travel"));
        assertTrue(game.world().isLevelLoaded("Hall"));
        assertTrue(game.gameMode() instanceof HallGameMode);

        Actor restored = game.world().findActor("HallBeacon");
        assertNotNull(restored);
        assertEquals(4.0f, restored.transform().location.x, 0.0001f);
        assertEquals(6.0f, restored.transform().location.y, 0.0001f);
        assertEquals(2.0f, restored.transform().location.z, 0.0001f);
        assertEquals(30.0f, restored.transform().rotation.yaw, 0.0001f);
        assertTrue(restored.getComponent(TagComponent.class).hasTag("beacon"));
        assertTrue(restored.getComponent(TagComponent.class).hasTag("saved"));
    }

    @Test
    public void deleteRemovesSlotAndMissingLoadFailsSoftly() {
        assertFalse(game.deleteGameInSlot("Ghost"));
        assertFalse(game.loadGameFromSlot("Ghost"));
        assertNull(game.loadSaveGameObject("Ghost"));

        assertTrue(game.saveGameToSlot("Keep"));
        assertTrue(game.saveGameToSlot("Drop"));
        assertTrue(game.deleteGameInSlot("Drop"));
        assertFalse(game.doesSaveGameExist("Drop"));
        assertTrue(game.doesSaveGameExist("Keep"));
        assertEquals(1, game.saveSlots().size());
        assertFalse(new File(saveDir, "Drop.sav").exists());
    }

    @Test
    public void pathTraversalSlotNamesAreRejected() {
        String[] bad = {"../escape", "foo/bar", "foo\\bar", "", " ", ".", "..", "slot.json"};
        for (String slot : bad) {
            try {
                game.saveGameToSlot(slot);
                fail("expected invalid slot: " + slot);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("slot"));
            }
            assertFalse(game.doesSaveGameExist(slot));
        }
        File[] leftovers = saveDir.listFiles();
        assertNotNull(leftovers);
        assertEquals(0, leftovers.length);
    }

    @Test
    public void jsonRoundTripPreservesPayload() {
        Actor beacon = game.world().findActor("HallBeacon");
        beacon.transform().location.set(1.0f, 2.0f, 3.0f);
        SaveGame save = game.createSaveGame();
        String json = save.toJson();
        assertTrue(json.contains("\"version\":"));
        assertTrue(json.contains("\"level\":\"Hall\""));
        assertTrue(json.contains("\"gameMode\":\"HallGameMode\""));
        assertTrue(json.contains("\"name\":\"HallBeacon\""));
        assertTrue(json.contains("\"location\""));

        SaveGame parsed = SaveGame.parse(json);
        assertEquals(save.version(), parsed.version());
        assertEquals("Hall", parsed.levelName());
        assertEquals("HallGameMode", parsed.gameMode());
        assertEquals(1.0f, parsed.findActor("HallBeacon").location().x, 0.0001f);
        assertEquals(2.0f, parsed.findActor("HallBeacon").location().y, 0.0001f);
        assertEquals(3.0f, parsed.findActor("HallBeacon").location().z, 0.0001f);
    }

    @Test
    public void gameplayStaticsReachSaveGameThroughWorld() {
        assertTrue(GameplayStatics.saveGameToSlot(game.world(), "ViaWorld"));
        assertTrue(GameplayStatics.doesSaveGameExist(game.world(), "ViaWorld"));
        SaveGame created = GameplayStatics.createSaveGame(game);
        assertEquals("Hall", created.levelName());
        assertTrue(GameplayStatics.deleteGameInSlot(game, "ViaWorld"));
        assertFalse(GameplayStatics.doesSaveGameExist(game, "ViaWorld"));
    }

    @Test
    public void consoleSaveAndLoadRestoreBeacon() {
        DeveloperConsole console = game.console();
        Actor beacon = game.world().findActor("HallBeacon");
        beacon.transform().location.set(9.0f, 1.0f, 7.0f);

        String saved = console.exec("SaveGame SlotA");
        assertTrue(saved.contains("saved SlotA"));
        assertTrue(saved.contains("level=Hall"));
        assertTrue(game.doesSaveGameExist("SlotA"));

        beacon.transform().location.set(0.0f, 1.5f, 4.0f);
        String loaded = console.exec("LoadGame SlotA");
        assertTrue(loaded.contains("loaded SlotA"));
        assertEquals(9.0f, game.world().findActor("HallBeacon").transform().location.x, 0.0001f);
        assertEquals(1.0f, game.world().findActor("HallBeacon").transform().location.y, 0.0001f);
        assertEquals(7.0f, game.world().findActor("HallBeacon").transform().location.z, 0.0001f);

        String missing = console.exec("loadgame Missing");
        assertTrue(missing.startsWith("error:"));
        assertTrue(missing.contains("no save"));
        assertTrue(console.exec("savegame").startsWith("error:"));
        assertTrue(console.exec("help").contains("savegame"));
        assertTrue(console.exec("help").contains("loadgame"));
    }

    @Test
    public void dumpAndStatIncludeSaveCount() {
        StringBuilder out = new StringBuilder();
        game.world().appendDump(out);
        assertTrue(out.toString().contains("game.saves=0"));

        game.saveGameToSlot("One");
        game.saveGameToSlot("Two");
        out.setLength(0);
        game.world().appendDump(out);
        assertTrue(out.toString().contains("game.saves=2"));

        String stat = game.console().exec("stat");
        assertTrue(stat.contains("saves=2"));
    }
}
