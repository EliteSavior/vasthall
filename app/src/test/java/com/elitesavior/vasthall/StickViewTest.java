package com.elitesavior.vasthall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class StickViewTest {
    private static final float EPSILON = 0.0001f;

    @Before
    public void resetTrace() {
        StickView.recordEvents = true;
        StickView.resetTrace();
    }

    @Test
    public void twentyDragsOutsideBoundsStayLiveAndZeroOnLift() {
        StickView zone = zone(true);
        float[] axes = listen(zone);

        for (int attempt = 0; attempt < 20; attempt++) {
            zone.onTouchEvent(event(3, MotionEvent.ACTION_DOWN, 100.0f, 250.0f));
            assertTrue(zone.hasActiveFinger());
            assertTrue(stickVisible(zone));

            zone.onTouchEvent(event(3, MotionEvent.ACTION_MOVE, 900.0f, -100.0f));
            assertTrue(zone.hasActiveFinger());
            assertEquals(1.0f, (float) Math.hypot(axes[0], axes[1]), EPSILON);

            zone.onTouchEvent(event(3, MotionEvent.ACTION_MOVE, 480.0f, 250.0f));
            assertTrue(zone.hasActiveFinger());
            assertTrue(Math.abs(axes[0]) > 0.9f);

            zone.onTouchEvent(event(3, MotionEvent.ACTION_UP, 480.0f, 250.0f));
            assertFalse(zone.hasActiveFinger());
            assertFalse(stickVisible(zone));
            assertEquals(0.0f, axes[0], EPSILON);
            assertEquals(0.0f, axes[1], EPSILON);
        }
    }

    @Test
    public void twoZonesReleaseIndependentlyWithoutFrozenAxes() {
        StickView left = zone(true);
        StickView right = zone(false);
        float[] moveAxes = listen(left);
        float[] lookAxes = listen(right);

        left.onTouchEvent(event(7, MotionEvent.ACTION_DOWN, 120.0f, 240.0f));
        right.onTouchEvent(event(11, MotionEvent.ACTION_DOWN, 380.0f, 220.0f));
        left.onTouchEvent(event(7, MotionEvent.ACTION_MOVE, 400.0f, 240.0f));
        right.onTouchEvent(event(11, MotionEvent.ACTION_MOVE, 180.0f, 420.0f));

        assertTrue(left.hasActiveFinger());
        assertTrue(right.hasActiveFinger());
        assertTrue(Math.hypot(moveAxes[0], moveAxes[1]) > 0.9);
        assertTrue(Math.hypot(lookAxes[0], lookAxes[1]) > 0.9);

        left.onTouchEvent(event(7, MotionEvent.ACTION_UP, 400.0f, 240.0f));
        assertFalse(left.hasActiveFinger());
        assertTrue(right.hasActiveFinger());
        assertEquals(0.0f, moveAxes[0], EPSILON);
        assertEquals(0.0f, moveAxes[1], EPSILON);
        assertTrue(Math.hypot(lookAxes[0], lookAxes[1]) > 0.9);

        right.onTouchEvent(event(11, MotionEvent.ACTION_UP, 180.0f, 420.0f));
        assertFalse(right.hasActiveFinger());
        assertEquals(0.0f, lookAxes[0], EPSILON);
        assertEquals(0.0f, lookAxes[1], EPSILON);
    }

    @Test
    public void cancelZerosAndLaterMoveDoesNotRevive() {
        StickView zone = zone(true);
        float[] axes = listen(zone);

        zone.onTouchEvent(event(4, MotionEvent.ACTION_DOWN, 100.0f, 100.0f));
        zone.onTouchEvent(event(4, MotionEvent.ACTION_MOVE, 300.0f, 100.0f));
        assertTrue(Math.abs(axes[0]) > 0.9f);

        zone.onTouchEvent(event(4, MotionEvent.ACTION_CANCEL, 300.0f, 100.0f));
        assertIdle(zone, axes);
        assertTrue(traceContains("left INPUT_ZERO reason=CANCEL"));
        for (String line : StickView.copyTrace()) {
            if (line.contains("INPUT_ZERO") || line.contains("INPUT_RECLAIM")) {
                System.out.println("LOCKUP_LOG " + line);
            }
        }

        // Ghost MOVE after lift/cancel must not re-press. A new DOWN starts a gesture.
        zone.onTouchEvent(event(4, MotionEvent.ACTION_MOVE, 300.0f, 100.0f));
        assertIdle(zone, axes);
        assertFalse(traceContains("INPUT_RECLAIM"));

        zone.onTouchEvent(event(5, MotionEvent.ACTION_DOWN, 100.0f, 100.0f));
        assertTrue(zone.hasActiveFinger());
        zone.onTouchEvent(event(5, MotionEvent.ACTION_UP, 100.0f, 100.0f));
        assertIdle(zone, axes);
    }

    @Test
    public void watchdogDoesNotZeroWhileFingerIsHeld() {
        StickView zone = zone(true);
        float[] axes = listen(zone);
        zone.onTouchEvent(event(1, MotionEvent.ACTION_DOWN, 100.0f, 100.0f));
        zone.onTouchEvent(event(1, MotionEvent.ACTION_MOVE, 300.0f, 100.0f));
        float held = axes[0];
        for (int i = 0; i < 300; i++) {
            zone.enforceIdle();
        }
        assertTrue(zone.hasActiveFinger());
        assertEquals(held, axes[0], EPSILON);
        assertFalse(traceContains("WATCHDOG"));
    }

    @Test
    public void twentySecondHoldStaysLiveWithNoTimeBasedZero() {
        StickView zone = zone(true);
        float[] axes = listen(zone);
        zone.onTouchEvent(timed(2, MotionEvent.ACTION_DOWN, 120.0f, 240.0f, 0L));
        for (int ms = 16; ms <= 20_000; ms += 16) {
            zone.onTouchEvent(timed(2, MotionEvent.ACTION_MOVE, 400.0f, 240.0f, ms));
            zone.enforceIdle();
            assertTrue("died at t=" + ms + " trace=" + StickView.copyTrace(),
                    zone.hasActiveFinger());
            assertTrue(Math.abs(axes[0]) > 0.9f);
        }
        assertFalse(traceContains("INPUT_ZERO"));
        zone.onTouchEvent(timed(2, MotionEvent.ACTION_UP, 400.0f, 240.0f, 20_016L));
        assertIdle(zone, axes);
        assertTrue(traceContains("left INPUT_ZERO reason=UP"));
    }

    @Test
    public void missingPointerZerosInsteadOfStealingAnotherFinger() {
        StickView zone = zone(true);
        float[] axes = listen(zone);
        zone.onTouchEvent(event(4, MotionEvent.ACTION_DOWN, 100.0f, 100.0f));
        zone.onTouchEvent(event(4, MotionEvent.ACTION_MOVE, 300.0f, 100.0f));
        zone.onTouchEvent(event(99, MotionEvent.ACTION_MOVE, 300.0f, 100.0f));
        assertIdle(zone, axes);
        assertTrue(traceContains("left INPUT_ZERO reason=MISSING_POINTER"));
        assertFalse(traceContains("INPUT_RECLAIM"));
        zone.enforceIdle();
        assertIdle(zone, axes);
    }

    @Test
    public void fixedHalfLayoutRetainsAndSplitsTouchTargetsAcrossBounds() {
        StickView left = zone(true);
        StickView right = zone(false);
        float[] moveAxes = listen(left);
        float[] lookAxes = listen(right);
        PlayHud hud = playHud(left, right, null);

        assertEquals(500, left.getWidth());
        assertEquals(500, right.getWidth());
        assertEquals(0, left.getLeft());
        assertEquals(500, right.getLeft());

        hud.dispatchTouchEvent(event(7, MotionEvent.ACTION_DOWN, 100.0f, 240.0f));
        hud.dispatchTouchEvent(event(7, MotionEvent.ACTION_MOVE, 800.0f, 240.0f));
        assertTrue(left.hasActiveFinger());
        assertFalse(right.hasActiveFinger());
        assertTrue(Math.abs(moveAxes[0]) > 0.9f);

        hud.dispatchTouchEvent(event(
                new int[]{7, 11},
                MotionEvent.ACTION_POINTER_DOWN
                        | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new float[]{800.0f, 850.0f},
                new float[]{240.0f, 200.0f}));
        hud.dispatchTouchEvent(event(
                new int[]{7, 11},
                MotionEvent.ACTION_MOVE,
                new float[]{900.0f, 600.0f},
                new float[]{240.0f, 400.0f}));
        assertTrue(left.hasActiveFinger());
        assertTrue(right.hasActiveFinger());
        assertTrue(Math.hypot(moveAxes[0], moveAxes[1]) > 0.9);
        assertTrue(Math.hypot(lookAxes[0], lookAxes[1]) > 0.9);

        hud.dispatchTouchEvent(event(
                new int[]{7, 11},
                MotionEvent.ACTION_POINTER_UP,
                new float[]{900.0f, 600.0f},
                new float[]{240.0f, 400.0f}));
        assertFalse(left.hasActiveFinger());
        assertTrue(right.hasActiveFinger());
        assertEquals(0.0f, moveAxes[0], EPSILON);
        assertEquals(0.0f, moveAxes[1], EPSILON);

        hud.dispatchTouchEvent(event(11, MotionEvent.ACTION_UP, 600.0f, 400.0f));
        assertFalse(right.hasActiveFinger());
        assertEquals(0.0f, lookAxes[0], EPSILON);
        assertEquals(0.0f, lookAxes[1], EPSILON);

        for (String line : StickView.copyTrace()) {
            if (line.contains(" EVENT ")) {
                assertTrue("shared stream leaked n>1: " + line, line.contains(" count=1 "));
            }
        }
    }

    @Test
    public void hudAxesMoveDoesNotInvokeNativeOnTheCallerThread() throws Exception {
        AtomicReference<Thread> sinkThread = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        HudAxes axes = new HudAxes(new HudAxes.NativeSink() {
            @Override
            public void setMove(float x, float y) {
                if (x == 1.0f) {
                    sinkThread.set(Thread.currentThread());
                    latch.countDown();
                }
            }

            @Override
            public void setLook(float x, float y) {
            }

            @Override
            public void setJump(boolean down) {
            }
        });
        axes.start();
        Thread caller = Thread.currentThread();
        axes.setMove(1.0f, 0.0f);
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertNotEquals(caller, sinkThread.get());
        assertEquals("hall-axis-pump", sinkThread.get().getName());
        axes.stop();
    }

    @Test
    public void pumpAppliesHeldAxesEveryTickWithoutChangeDetection() throws Exception {
        java.util.concurrent.atomic.AtomicInteger moves = new java.util.concurrent.atomic.AtomicInteger();
        HudAxes axes = new HudAxes(new HudAxes.NativeSink() {
            @Override
            public void setMove(float x, float y) {
                if (x == 0.5f) {
                    moves.incrementAndGet();
                }
            }

            @Override
            public void setLook(float x, float y) {
            }

            @Override
            public void setJump(boolean down) {
            }
        });
        axes.start();
        axes.setMove(0.5f, 0.0f);
        Thread.sleep(40);
        assertTrue("native apply throttled, count=" + moves.get(), moves.get() >= 3);
        long lag = axes.jniLagMs();
        assertTrue("jniLagMs climbed: " + lag, lag < 33L);
        axes.stop();
    }

    @Test
    public void twentySecondDualHoldThroughPlayHudNeverSeesN2() {
        StickView left = zone(true);
        StickView right = zone(false);
        float[] moveAxes = listen(left);
        float[] lookAxes = listen(right);
        PlayHud hud = playHud(left, right, null);

        hud.dispatchTouchEvent(timed(7, MotionEvent.ACTION_DOWN, 100.0f, 240.0f, 0L));
        hud.dispatchTouchEvent(event(
                new int[]{7, 11},
                MotionEvent.ACTION_POINTER_DOWN
                        | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new float[]{100.0f, 850.0f},
                new float[]{240.0f, 200.0f},
                16L));
        for (int ms = 32; ms <= 20_000; ms += 16) {
            hud.dispatchTouchEvent(event(
                    new int[]{7, 11},
                    MotionEvent.ACTION_MOVE,
                    new float[]{400.0f, 700.0f},
                    new float[]{240.0f, 400.0f},
                    ms));
            assertTrue(left.hasActiveFinger());
            assertTrue(right.hasActiveFinger());
        }
        for (String line : StickView.copyTrace()) {
            if (line.contains(" EVENT ")) {
                assertTrue("n>1 leaked: " + line, line.contains(" count=1 "));
            }
        }
        hud.dispatchTouchEvent(event(
                new int[]{7, 11},
                MotionEvent.ACTION_POINTER_UP,
                new float[]{400.0f, 700.0f},
                new float[]{240.0f, 400.0f},
                20_016L));
        assertFalse(left.hasActiveFinger());
        assertTrue(right.hasActiveFinger());
        assertEquals(0.0f, moveAxes[0], EPSILON);
        assertTrue(Math.hypot(lookAxes[0], lookAxes[1]) > 0.5);
        hud.dispatchTouchEvent(event(13, MotionEvent.ACTION_DOWN, 120.0f, 240.0f));
        assertTrue(left.hasActiveFinger());
    }

    @Test
    public void foreignActionUpDoesNotZeroTheOtherZone() {
        StickView left = zone(true);
        StickView right = zone(false);
        float[] moveAxes = listen(left);
        float[] lookAxes = listen(right);

        left.onTouchEvent(event(7, MotionEvent.ACTION_DOWN, 120.0f, 240.0f));
        right.onTouchEvent(event(11, MotionEvent.ACTION_DOWN, 380.0f, 220.0f));
        left.onTouchEvent(event(7, MotionEvent.ACTION_MOVE, 400.0f, 240.0f));
        right.onTouchEvent(event(11, MotionEvent.ACTION_MOVE, 180.0f, 420.0f));
        float liveLook = (float) Math.hypot(lookAxes[0], lookAxes[1]);
        assertTrue(liveLook > 0.9f);

        // Shared/unsplit ACTION_UP for the left finger must not call right.recenter("UP").
        right.onTouchEvent(event(7, MotionEvent.ACTION_UP, 400.0f, 240.0f));
        assertTrue(right.hasActiveFinger());
        assertEquals(11, right.pointerId());
        assertEquals(liveLook, (float) Math.hypot(lookAxes[0], lookAxes[1]), EPSILON);
        assertFalse(traceContains("right INPUT_ZERO reason=UP"));

        left.onTouchEvent(event(7, MotionEvent.ACTION_UP, 400.0f, 240.0f));
        assertFalse(left.hasActiveFinger());
        assertEquals(0.0f, moveAxes[0], EPSILON);
        assertTrue(right.hasActiveFinger());

        left.onTouchEvent(event(8, MotionEvent.ACTION_DOWN, 120.0f, 240.0f));
        assertTrue(left.hasActiveFinger());
        assertEquals(8, left.pointerId());

        right.onTouchEvent(event(11, MotionEvent.ACTION_UP, 180.0f, 420.0f));
        assertFalse(right.hasActiveFinger());
    }

    @Test
    public void playHudOneUpDoesNotZeroTheOtherStickOrReviveOnGhostMove() {
        StickView left = zone(true);
        StickView right = zone(false);
        float[] moveAxes = listen(left);
        float[] lookAxes = listen(right);
        PlayHud hud = playHud(left, right, null);

        hud.dispatchTouchEvent(event(7, MotionEvent.ACTION_DOWN, 120.0f, 240.0f));
        hud.dispatchTouchEvent(event(
                new int[]{7, 11},
                MotionEvent.ACTION_POINTER_DOWN
                        | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new float[]{120.0f, 700.0f},
                new float[]{240.0f, 220.0f}));
        hud.dispatchTouchEvent(event(
                new int[]{7, 11},
                MotionEvent.ACTION_MOVE,
                new float[]{400.0f, 700.0f},
                new float[]{240.0f, 400.0f}));
        assertTrue(left.hasActiveFinger());
        assertTrue(right.hasActiveFinger());

        hud.dispatchTouchEvent(event(
                new int[]{7, 11},
                MotionEvent.ACTION_POINTER_UP,
                new float[]{400.0f, 700.0f},
                new float[]{240.0f, 400.0f}));
        assertFalse(left.hasActiveFinger());
        assertTrue(right.hasActiveFinger());
        assertEquals(0.0f, moveAxes[0], EPSILON);
        assertTrue(Math.hypot(lookAxes[0], lookAxes[1]) > 0.9);

        hud.dispatchTouchEvent(event(7, MotionEvent.ACTION_MOVE, 400.0f, 240.0f));
        assertIdle(left, moveAxes);
        assertTrue(right.hasActiveFinger());

        hud.dispatchTouchEvent(event(13, MotionEvent.ACTION_DOWN, 120.0f, 240.0f));
        assertTrue(left.hasActiveFinger());
    }

    @Test
    public void jumpWhileMovingDoesNotStealLookStick() {
        StickView left = zone(true);
        StickView right = zone(false);
        float[] moveAxes = listen(left);
        float[] lookAxes = listen(right);
        View jump = new View(RuntimeEnvironment.getApplication());
        FrameLayout.LayoutParams jumpLp = new FrameLayout.LayoutParams(80, 80);
        jumpLp.gravity = Gravity.TOP | Gravity.START;
        jumpLp.leftMargin = 920;
        jumpLp.topMargin = 420;
        jump.setLayoutParams(jumpLp);
        final boolean[] jumpDown = {false};
        jump.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                jumpDown[0] = true;
            } else if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL) {
                jumpDown[0] = false;
            } else if (action == MotionEvent.ACTION_POINTER_UP) {
                throw new AssertionError("jump must not see a second pointer");
            }
            return true;
        });
        PlayHud hud = playHud(left, right, jump);

        hud.dispatchTouchEvent(event(7, MotionEvent.ACTION_DOWN, 100.0f, 240.0f));
        hud.dispatchTouchEvent(event(7, MotionEvent.ACTION_MOVE, 400.0f, 240.0f));
        assertTrue(Math.abs(moveAxes[0]) > 0.9f);
        assertFalse(right.hasActiveFinger());

        hud.dispatchTouchEvent(event(
                new int[]{7, 11},
                MotionEvent.ACTION_POINTER_DOWN
                        | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new float[]{400.0f, 960.0f},
                new float[]{240.0f, 460.0f}));
        assertTrue(jumpDown[0]);
        assertFalse(right.hasActiveFinger());
        assertEquals(0.0f, lookAxes[0], EPSILON);
        assertEquals(PlayInputMachine.Target.JUMP, hud.machine().targetOf(11));

        hud.dispatchTouchEvent(event(
                new int[]{7, 11},
                MotionEvent.ACTION_POINTER_UP
                        | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new float[]{400.0f, 960.0f},
                new float[]{240.0f, 460.0f}));
        assertFalse(jumpDown[0]);
        assertTrue(left.hasActiveFinger());
        assertEquals(PlayInputMachine.Target.MOVE, hud.machine().targetOf(7));
    }

    @Test
    public void cancelAllReleasesSticksJumpAndKeys() {
        StickView left = zone(true);
        StickView right = zone(false);
        float[] moveAxes = listen(left);
        float[] lookAxes = listen(right);
        PlayHud hud = playHud(left, right, null);
        hud.machine().keyDown(51);
        hud.dispatchTouchEvent(event(7, MotionEvent.ACTION_DOWN, 100.0f, 240.0f));
        hud.dispatchTouchEvent(event(7, MotionEvent.ACTION_MOVE, 400.0f, 240.0f));
        hud.dispatchTouchEvent(event(
                new int[]{7, 11},
                MotionEvent.ACTION_POINTER_DOWN
                        | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new float[]{400.0f, 800.0f},
                new float[]{240.0f, 240.0f}));
        hud.cancelAll("PAUSE");
        assertIdle(left, moveAxes);
        assertIdle(right, lookAxes);
        assertFalse(hud.machine().anyPressed());
        assertFalse(hud.machine().isKeyDown(51));
    }

    @Test
    public void foreignCancelDoesNotZeroTrackedZone() {
        StickView right = zone(false);
        float[] lookAxes = listen(right);
        right.onTouchEvent(event(11, MotionEvent.ACTION_DOWN, 380.0f, 220.0f));
        right.onTouchEvent(event(11, MotionEvent.ACTION_MOVE, 180.0f, 420.0f));
        right.onTouchEvent(event(7, MotionEvent.ACTION_CANCEL, 400.0f, 240.0f));
        assertTrue(right.hasActiveFinger());
        assertTrue(Math.hypot(lookAxes[0], lookAxes[1]) > 0.9);
        assertFalse(traceContains("right INPUT_ZERO reason=CANCEL"));
    }

    private static boolean traceContains(String needle) {
        List<String> lines = StickView.copyTrace();
        for (String line : lines) {
            if (line.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static PlayHud playHud(StickView left, StickView right, View jump) {
        PlayHud hud = new PlayHud(
                RuntimeEnvironment.getApplication(),
                new PlayInputMachine(),
                left,
                right,
                jump);
        hud.measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY));
        hud.layout(0, 0, 1000, 500);
        return hud;
    }

    private static StickView zone(boolean left) {
        StickView zone = new StickView(RuntimeEnvironment.getApplication(), left);
        zone.layout(0, 0, 500, 500);
        return zone;
    }

    private static float[] listen(StickView zone) {
        float[] axes = new float[2];
        zone.setListener((x, y) -> {
            axes[0] = x;
            axes[1] = y;
        });
        return axes;
    }

    private static MotionEvent event(
            int pointerId, int action, float x, float y) {
        return timed(pointerId, action, x, y, 2L);
    }

    private static MotionEvent timed(
            int pointerId, int action, float x, float y, long eventTime) {
        return event(
                new int[]{pointerId},
                action,
                new float[]{x},
                new float[]{y},
                eventTime);
    }

    private static MotionEvent event(
            int[] pointerIds, int action, float[] xs, float[] ys) {
        return event(pointerIds, action, xs, ys, 2L);
    }

    private static MotionEvent event(
            int[] pointerIds, int action, float[] xs, float[] ys, long eventTime) {
        MotionEvent.PointerProperties[] properties =
                new MotionEvent.PointerProperties[pointerIds.length];
        MotionEvent.PointerCoords[] coords =
                new MotionEvent.PointerCoords[pointerIds.length];
        for (int i = 0; i < pointerIds.length; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = pointerIds[i];
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = xs[i];
            coords[i].y = ys[i];
            coords[i].pressure = 1.0f;
            coords[i].size = 1.0f;
        }
        return MotionEvent.obtain(
                1L,
                eventTime,
                action,
                pointerIds.length,
                properties,
                coords,
                0,
                0,
                1.0f,
                1.0f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0);
    }

    private static boolean stickVisible(StickView zone) {
        return ReflectionHelpers.getField(zone, "drawStick");
    }

    private static void assertIdle(StickView zone, float[] axes) {
        assertFalse(zone.hasActiveFinger());
        assertFalse(stickVisible(zone));
        assertEquals(0.0f, axes[0], EPSILON);
        assertEquals(0.0f, axes[1], EPSILON);
    }
}
