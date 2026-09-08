package com.elitesavior.vasthall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class FlatPadOverlayTest {
    private static final float EPSILON = 0.0001f;

    private FlatPadRouter router;
    private FlatPadOverlay overlay;
    private RecordingSink sink;

    @Before
    public void setUp() {
        sink = new RecordingSink();
        router = new FlatPadRouter();
        router.setSink(sink);
        overlay = new FlatPadOverlay(RuntimeEnvironment.getApplication(), router);
        overlay.setJumpChrome(72.0f, 128.0f, 48.0f, "Jump");
        overlay.measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
        overlay.layout(0, 0, 1000, 600);
        overlay.setPlayVisible(true);
    }

    @Test
    public void downMoveUpUsesPointerIdNotActionIndex() {
        float r = Math.max(router.layout().stickRadius, 1.0f);
        assertTrue(overlay.onTouchEvent(event(7, MotionEvent.ACTION_DOWN, 100.0f, 200.0f)));
        assertEquals(7, router.ownerOf(FlatPadRouter.Target.MOVE));
        overlay.onTouchEvent(event(7, MotionEvent.ACTION_MOVE, 100.0f + r, 200.0f));
        assertEquals(1.0f, router.moveX(), EPSILON);
        overlay.onTouchEvent(event(7, MotionEvent.ACTION_UP, 100.0f + r, 200.0f));
        assertFalse(router.hasPointer(7));
        assertEquals(0.0f, sink.moveX, EPSILON);
    }

    @Test
    public void cancelWithTwoPointersMidDeflectionZerosAll() {
        float r = Math.max(router.layout().stickRadius, 1.0f);
        overlay.onTouchEvent(event(7, MotionEvent.ACTION_DOWN, 100.0f, 200.0f));
        overlay.onTouchEvent(event(7, MotionEvent.ACTION_MOVE, 100.0f + r, 200.0f));
        overlay.onTouchEvent(event(
                new int[] {7, 11},
                MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new float[] {100.0f + r, 700.0f},
                new float[] {200.0f, 200.0f}));
        overlay.onTouchEvent(event(
                new int[] {7, 11},
                MotionEvent.ACTION_MOVE,
                new float[] {100.0f + r, 700.0f + r},
                new float[] {200.0f, 200.0f}));
        assertEquals(1.0f, router.moveX(), EPSILON);
        assertEquals(1.0f, router.lookX(), EPSILON);

        overlay.onTouchEvent(event(
                new int[] {7, 11},
                MotionEvent.ACTION_CANCEL,
                new float[] {171.0f, 771.0f},
                new float[] {200.0f, 200.0f}));
        assertFalse(router.anyPressed());
        assertEquals(0.0f, sink.moveX, EPSILON);
        assertEquals(0.0f, sink.lookX, EPSILON);
        assertEquals("CANCEL", router.lastWhoZeroed());
    }

    @Test
    public void actionOutsideFlushesLikeCancel() {
        float r = Math.max(router.layout().stickRadius, 1.0f);
        overlay.onTouchEvent(event(7, MotionEvent.ACTION_DOWN, 100.0f, 200.0f));
        overlay.onTouchEvent(event(7, MotionEvent.ACTION_MOVE, 100.0f + r, 200.0f));
        overlay.onTouchEvent(event(7, MotionEvent.ACTION_OUTSIDE, 100.0f + r, 200.0f));
        assertFalse(router.anyPressed());
        assertEquals(0.0f, sink.moveX, EPSILON);
        assertEquals("CANCEL", router.lastWhoZeroed());
    }

    @Test
    public void moveWithoutOwnerPointerOrphansAndZeros() {
        float r = Math.max(router.layout().stickRadius, 1.0f);
        overlay.onTouchEvent(event(7, MotionEvent.ACTION_DOWN, 100.0f, 200.0f));
        overlay.onTouchEvent(event(7, MotionEvent.ACTION_MOVE, 100.0f + r, 200.0f));
        assertEquals(1.0f, router.moveX(), EPSILON);

        overlay.onTouchEvent(event(99, MotionEvent.ACTION_MOVE, 400.0f, 200.0f));
        assertFalse(router.hasPointer(7));
        assertEquals(0.0f, router.moveX(), EPSILON);
        assertEquals(0.0f, sink.moveX, EPSILON);
        assertEquals("ORPHAN", router.lastWhoZeroed());
    }

    @Test
    public void moveDrainsHistoricalSamplesThenCurrent() {
        float r = Math.max(router.layout().stickRadius, 1.0f);
        overlay.onTouchEvent(event(7, MotionEvent.ACTION_DOWN, 100.0f, 200.0f));
        MotionEvent move = event(7, MotionEvent.ACTION_MOVE, 100.0f + r * 0.5f, 200.0f);
        MotionEvent.PointerCoords next = new MotionEvent.PointerCoords();
        next.x = 100.0f + r;
        next.y = 200.0f;
        next.pressure = 1.0f;
        next.size = 1.0f;
        move.addBatch(4L, new MotionEvent.PointerCoords[] {next}, 0);
        assertTrue(move.getHistorySize() >= 1);
        overlay.onTouchEvent(move);
        assertEquals(1.0f, router.moveX(), EPSILON);
        assertTrue(sink.moveXs.size() >= 2);
        assertEquals(1.0f, sink.moveXs.get(sink.moveXs.size() - 1), EPSILON);
        float historical = sink.moveXs.get(sink.moveXs.size() - 2);
        assertTrue("historical sample should be partial, was " + historical,
                historical > 0.4f && historical < 0.6f);
    }

    private static MotionEvent event(int pointerId, int action, float x, float y) {
        return event(new int[] {pointerId}, action, new float[] {x}, new float[] {y});
    }

    private static MotionEvent event(int[] pointerIds, int action, float[] xs, float[] ys) {
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
                2L,
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

    private static final class RecordingSink implements FlatPadRouter.Sink {
        float moveX;
        float lookX;
        final List<Float> moveXs = new ArrayList<>();

        @Override
        public void setMove(float x, float y) {
            moveX = x;
            moveXs.add(x);
        }

        @Override
        public void setLook(float x, float y) {
            lookX = x;
        }

        @Override
        public void setJump(boolean down) {
        }
    }
}
