package com.elitesavior.vasthall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import android.view.MotionEvent;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class DebugMotionOverlayTest {
    @Test
    public void overlayDoesNotConsumeTouchesAndKeepsSnapshot() {
        DebugMotionOverlay overlay = new DebugMotionOverlay(RuntimeEnvironment.getApplication());
        overlay.measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
        overlay.layout(0, 0, 1000, 600);
        DebugHub.HudSnapshot snap = new DebugHub.HudSnapshot(
                "flat own M7 L-1 J-1\nFLAGS INPUT_ZERO_MOTION_NONZERO",
                0.8f, 0.0f, 0.8f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                new float[] {0.1f, 0.8f}, new float[] {0.1f, 0.8f}, new float[] {0.0f, 0.2f},
                MotionCorr.INPUT_ZERO_MOTION_NONZERO);
        overlay.setSnapshot(snap);
        overlay.setDebugVisible(true);
        assertSame(snap, overlay.snapshot());
        assertEquals(View.VISIBLE, overlay.getVisibility());
        MotionEvent down = MotionEvent.obtain(1L, 2L, MotionEvent.ACTION_DOWN, 10.0f, 10.0f, 0);
        assertFalse(overlay.onTouchEvent(down));
        down.recycle();
        overlay.setDebugVisible(false);
        assertEquals(View.GONE, overlay.getVisibility());
    }
}
