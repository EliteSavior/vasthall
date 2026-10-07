package com.elitesavior.vasthall.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class DeveloperConsoleTest {
    private World world;
    private DeveloperConsole console;

    @Before
    public void setUp() {
        world = new World(AssetRegistry.withDemoAssets());
        console = DeveloperConsole.withBuiltins(world);
    }

    @Test
    public void registerThenDispatchNamedCommand() {
        AtomicInteger calls = new AtomicInteger();
        console.register("ping", "Echo ping", (bound, args) -> {
            calls.incrementAndGet();
            return "pong " + (args.length == 0 ? "" : args[0]);
        });

        assertTrue(console.isRegistered("ping"));
        assertTrue(console.commandNames().contains("ping"));
        assertEquals("Echo ping", console.helpText("ping"));
        assertEquals("pong ", console.exec("ping"));
        assertEquals("pong hall", console.exec("  PING   hall  "));
        assertEquals(2, calls.get());
    }

    @Test
    public void unknownCommandAndEmptyLine() {
        assertEquals("", console.exec(""));
        assertEquals("", console.exec("   "));
        String missing = console.exec("nope");
        assertTrue(missing.contains("unknown command: nope"));
        assertTrue(missing.toLowerCase().contains("help"));
    }

    @Test
    public void duplicateRegisterIsRejected() {
        console.register("custom", "once", (bound, args) -> "ok");
        try {
            console.register("CUSTOM", "twice", (bound, args) -> "no");
            fail("expected duplicate command");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("custom"));
        }
        assertEquals("ok", console.exec("custom"));
    }

    @Test
    public void helpListsBuiltinsAndSpecificHelp() {
        String all = console.exec("help");
        assertTrue(all.contains("actors"));
        assertTrue(all.contains("assets"));
        assertTrue(all.contains("load"));
        assertTrue(all.contains("unload"));
        assertTrue(all.contains("open"));
        assertTrue(all.contains("help"));
        assertTrue(all.contains("settimer"));
        assertTrue(all.contains("cleartimer"));
        assertTrue(all.contains("timers"));
        assertTrue(all.contains("events"));
        assertTrue(all.contains("savegame"));
        assertTrue(all.contains("loadgame"));
        assertTrue(all.contains("playsound"));
        assertTrue(all.contains("stopsound"));
        assertTrue(all.contains("setmastervolume"));
        assertTrue(all.contains("audio"));
        assertTrue(all.contains("createwidget"));
        assertTrue(all.contains("addtoviewport"));
        assertTrue(all.contains("hidewidget"));
        assertTrue(all.contains("showwidget"));
        assertTrue(all.contains("removefromparent"));
        assertTrue(all.contains("widgets"));
        assertTrue(all.contains("listoverlaps"));
        assertTrue(all.contains("debugdrawoverlaps"));
        String one = console.exec("help actors");
        assertTrue(one.toLowerCase().contains("actor"));
        assertEquals(console.helpText("actors"), one.trim());
    }

    @Test
    public void actorsListsWorldActors() {
        world.openLevel("Hall");
        String out = console.exec("actors");
        assertTrue(out.contains("actors=2"));
        assertTrue(out.contains("PlayerPawn"));
        assertTrue(out.contains("HallBeacon"));
        assertTrue(out.contains("class=HallBeaconActor"));
        assertTrue(out.contains("level=Hall"));
    }

    @Test
    public void assetsListsRegistry() {
        String out = console.exec("assets");
        assertTrue(out.contains("assets=7"));
        assertTrue(out.contains("id=Hall"));
        assertTrue(out.contains("id=IsoSandbox"));
        assertTrue(out.contains("path=levels/Hall.json"));
        assertTrue(out.contains("kind=LEVEL"));
        assertTrue(out.contains("id=HallMesh"));
        assertTrue(out.contains("id=HallBeaconTexture"));
        assertTrue(out.contains("id=HallAmbience"));
        assertTrue(out.contains("id=DefaultMapping"));
        assertTrue(out.contains("kind=INPUT_MAPPING"));
        assertTrue(out.contains("id=HallBlade"));
        assertTrue(out.contains("kind=DATA"));
        assertTrue(out.contains("type=WeaponDataAsset"));
        assertTrue(out.contains("gtags=Item.Weapon.Melee"));
    }

    @Test
    public void loadUnloadAndOpenTouchWorldLevels() {
        String loaded = console.exec("load Hall");
        assertTrue(loaded.contains("loaded Hall"));
        assertTrue(world.isLevelLoaded("Hall"));
        assertEquals(2, world.actorCount());
        assertTrue(loaded.contains("actors=2"));

        String already = console.exec("loadlevel Hall");
        assertTrue(already.contains("loaded Hall"));
        assertEquals(2, world.actorCount());

        String unloaded = console.exec("unload Hall");
        assertTrue(unloaded.contains("unloaded Hall"));
        assertFalse(world.isLevelLoaded("Hall"));
        assertEquals(0, world.actorCount());

        String missing = console.exec("unload Hall");
        assertTrue(missing.contains("not loaded"));

        String opened = console.exec("open levels/Hall.json");
        assertTrue(opened.contains("opened Hall"));
        assertTrue(world.isLevelLoaded("Hall"));
        assertEquals(2, world.actorCount());
    }

    @Test
    public void loadMissingLevelReturnsError() {
        String out = console.exec("load MissingMap");
        assertTrue(out.startsWith("error:"));
        assertTrue(out.contains("unknown level"));
        assertFalse(world.isLevelLoaded("MissingMap"));
    }

    @Test
    public void loadAndUnloadRequireAName() {
        assertTrue(console.exec("load").startsWith("error:"));
        assertTrue(console.exec("unload").startsWith("error:"));
        assertTrue(console.exec("open").startsWith("error:"));
    }

    @Test
    public void statReportsWorldCounts() {
        world.openLevel("Hall");
        String out = console.exec("stat");
        assertTrue(out.contains("actors=2"));
        assertTrue(out.contains("levels=1"));
        assertTrue(out.contains("assets=7"));
        assertTrue(out.contains("frame=0"));
        assertTrue(out.contains("mode=-"));
        assertTrue(out.contains("timers=0"));
        assertTrue(out.contains("saves=0"));
        assertTrue(out.contains("audio=0"));
        assertTrue(out.contains("widgets=0"));
        assertTrue(out.contains("overlaps=0"));
    }

    @Test
    public void execAppendsToLog() {
        console.exec("help");
        console.exec("assets");
        List<String> log = console.log();
        assertEquals(2, log.size());
        assertTrue(log.get(0).startsWith("> help"));
        assertTrue(log.get(1).startsWith("> assets"));
        console.clearLog();
        assertTrue(console.log().isEmpty());
    }
}
