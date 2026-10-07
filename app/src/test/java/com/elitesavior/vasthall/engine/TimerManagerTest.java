package com.elitesavior.vasthall.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class TimerManagerTest {
    private TimerManager timers;

    @Before
    public void setUp() {
        timers = new TimerManager();
    }

    @Test
    public void setTimerDoesNotFireUntilDelayElapses() {
        AtomicInteger fires = new AtomicInteger();
        TimerHandle handle = timers.setTimer(fires::incrementAndGet, 0.25f, false);

        assertTrue(handle.isValid());
        assertTrue(timers.isTimerActive(handle));
        assertFalse(timers.isTimerPaused(handle));
        assertEquals(0.25f, timers.getTimerRemaining(handle), 0.0001f);
        assertEquals(0.25f, timers.getTimerRate(handle), 0.0001f);
        assertEquals(1, timers.timerCount());

        timers.tick(0.10f);
        assertEquals(0, fires.get());
        assertEquals(0.15f, timers.getTimerRemaining(handle), 0.0001f);
        assertTrue(timers.isTimerActive(handle));

        timers.tick(0.14f);
        assertEquals(0, fires.get());
        assertTrue(handle.isValid());
    }

    @Test
    public void oneShotFiresOnceThenClears() {
        AtomicInteger fires = new AtomicInteger();
        TimerHandle handle = timers.setTimer(fires::incrementAndGet, 0.20f, false);

        timers.tick(0.20f);

        assertEquals(1, fires.get());
        assertEquals(0, timers.timerCount());
        assertFalse(handle.isValid());
        assertFalse(timers.isTimerActive(handle));
        assertEquals(-1.0f, timers.getTimerRemaining(handle), 0.0001f);
    }

    @Test
    public void loopingTimerFiresEachRateAndStaysActive() {
        AtomicInteger fires = new AtomicInteger();
        TimerHandle handle = timers.setTimer(fires::incrementAndGet, 0.10f, true);

        timers.tick(0.10f);
        timers.tick(0.10f);
        timers.tick(0.05f);

        assertEquals(2, fires.get());
        assertTrue(handle.isValid());
        assertTrue(timers.isTimerActive(handle));
        assertEquals(0.05f, timers.getTimerRemaining(handle), 0.0001f);
        assertEquals(1, timers.timerCount());
    }

    @Test
    public void loopingTimerCatchesUpWhenTickExceedsRate() {
        AtomicInteger fires = new AtomicInteger();
        timers.setTimer(fires::incrementAndGet, 0.10f, true);

        timers.tick(0.35f);

        assertEquals(3, fires.get());
        assertEquals(1, timers.timerCount());
    }

    @Test
    public void clearTimerPreventsFire() {
        AtomicInteger fires = new AtomicInteger();
        TimerHandle handle = timers.setTimer(fires::incrementAndGet, 0.10f, true);

        timers.tick(0.05f);
        timers.clearTimer(handle);
        timers.tick(1.00f);

        assertEquals(0, fires.get());
        assertFalse(handle.isValid());
        assertFalse(timers.isTimerActive(handle));
        assertEquals(0, timers.timerCount());
    }

    @Test
    public void clearTimerOnNullOrInvalidIsNoOp() {
        timers.clearTimer(null);
        timers.clearTimer(new TimerHandle());
        assertEquals(0, timers.timerCount());
    }

    @Test
    public void pauseFreezesRemainingAndUnpauseResumes() {
        AtomicInteger fires = new AtomicInteger();
        TimerHandle handle = timers.setTimer(fires::incrementAndGet, 0.40f, false);

        timers.tick(0.10f);
        timers.pauseTimer(handle);

        assertTrue(timers.isTimerPaused(handle));
        assertFalse(timers.isTimerActive(handle));
        assertEquals(0.30f, timers.getTimerRemaining(handle), 0.0001f);

        timers.tick(1.00f);
        assertEquals(0, fires.get());
        assertEquals(0.30f, timers.getTimerRemaining(handle), 0.0001f);

        timers.unPauseTimer(handle);
        assertFalse(timers.isTimerPaused(handle));
        assertTrue(timers.isTimerActive(handle));

        timers.tick(0.30f);
        assertEquals(1, fires.get());
        assertFalse(handle.isValid());
    }

    @Test
    public void callbackMayClearSelfOrSetAnotherTimer() {
        AtomicInteger innerFires = new AtomicInteger();
        List<String> order = new ArrayList<>();
        TimerHandle[] looping = new TimerHandle[1];
        looping[0] = timers.setTimer(() -> {
            order.add("loop");
            timers.clearTimer(looping[0]);
            timers.setTimer(() -> {
                order.add("inner");
                innerFires.incrementAndGet();
            }, 0.10f, false);
        }, 0.10f, true);

        timers.tick(0.10f);
        assertEquals(1, timers.timerCount());
        timers.tick(0.10f);

        assertEquals(1, innerFires.get());
        assertEquals(List.of("loop", "inner"), order);
        assertEquals(0, timers.timerCount());
    }

    @Test
    public void clearAllRemovesEveryTimer() {
        AtomicInteger fires = new AtomicInteger();
        timers.setTimer(fires::incrementAndGet, 0.10f, false);
        timers.setTimer(fires::incrementAndGet, 0.20f, true);
        timers.clearAll();
        timers.tick(1.00f);
        assertEquals(0, fires.get());
        assertEquals(0, timers.timerCount());
    }

    @Test
    public void rejectsNullCallbackAndNonPositiveRate() {
        try {
            timers.setTimer(null, 0.10f, false);
            fail("expected callback");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("callback"));
        }
        try {
            timers.setTimer(() -> { }, 0.0f, false);
            fail("expected rate");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("rate"));
        }
        try {
            timers.setTimer(() -> { }, -1.0f, true);
            fail("expected rate");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("rate"));
        }
        assertEquals(0, timers.timerCount());
    }

    @Test
    public void worldTickAdvancesOwnedTimerManager() {
        World world = new World();
        AtomicInteger fires = new AtomicInteger();
        TimerHandle handle = world.timerManager().setTimer(fires::incrementAndGet, 0.05f, false);
        assertSame(world.timerManager(), world.timerManager());
        world.tick(0.05f);
        assertEquals(1, fires.get());
        assertFalse(handle.isValid());
    }

    @Test
    public void gameInstanceExposesTheWorldTimerManager() {
        GameInstance game = GameInstance.withDemoAssets();
        game.init();
        assertNotNull(game.timerManager());
        assertSame(game.world().timerManager(), game.timerManager());
        assertSame(game.timerManager(), GameplayStatics.getTimerManager(game.world()));
    }

    @Test
    public void actorSetTimerClearsOnDestroy() {
        World world = new World();
        ProbeActor actor = world.spawnActor(ProbeActor.class);
        AtomicInteger fires = new AtomicInteger();
        TimerHandle handle = actor.setTimer(fires::incrementAndGet, 0.10f, true);
        assertTrue(world.timerManager().isTimerActive(handle));
        assertSame(world.timerManager(), actor.timerManager());

        world.destroyActor(actor);
        world.tick(1.00f);
        assertEquals(0, fires.get());
        assertEquals(0, world.timerManager().timerCount());
    }

    @Test
    public void componentSetTimerClearsOnDetach() {
        World world = new World();
        Actor actor = world.spawnActor(Actor.class);
        ProbeComponent component = actor.addComponent(new ProbeComponent());
        AtomicInteger fires = new AtomicInteger();
        TimerHandle handle = component.setTimer(fires::incrementAndGet, 0.10f, true);
        assertSame(world.timerManager(), component.timerManager());
        assertTrue(world.timerManager().isTimerActive(handle));

        actor.removeComponent(component);
        world.tick(1.00f);
        assertEquals(0, fires.get());
        assertEquals(0, world.timerManager().timerCount());
    }

    @Test
    public void hallGameModeSchedulesDelayedStartHook() {
        GameInstance game = GameInstance.withDemoAssets();
        game.init();
        game.openLevel("Hall");

        HallGameMode mode = (HallGameMode) game.gameMode();
        assertEquals(0, mode.delayedStartCount());
        assertEquals(1, game.timerManager().timerCount());

        game.world().tick(HallGameMode.DELAYED_START_SECONDS);
        assertEquals(1, mode.delayedStartCount());
        assertEquals(0, game.timerManager().timerCount());
    }

    @Test
    public void consoleSetTimerFiresIntoLogAfterWorldTick() {
        GameInstance game = GameInstance.withDemoAssets();
        game.init();
        DeveloperConsole console = game.console();

        String set = console.exec("settimer 0.2 once hello");
        assertTrue(set.contains("set 0.20s"));
        assertTrue(set.contains("once"));
        assertEquals(1, game.timerManager().timerCount());

        String listed = console.exec("timers");
        assertTrue(listed.contains("timers=1"));

        game.world().tick(0.20f);
        List<String> log = console.log();
        boolean fired = false;
        for (String line : log) {
            if (line.contains("timer fired") && line.contains("hello")) {
                fired = true;
                break;
            }
        }
        assertTrue(fired);

        String cleared = console.exec("settimer 1 loop keep");
        assertTrue(cleared.contains("loop"));
        assertEquals(1, game.timerManager().timerCount());
        assertTrue(console.exec("cleartimer").contains("cleared"));
        assertEquals(0, game.timerManager().timerCount());
        game.world().tick(1.00f);
    }

    public static final class ProbeActor extends Actor {
    }

    public static final class ProbeComponent extends ActorComponent {
    }
}
