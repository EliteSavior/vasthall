package com.elitesavior.vasthall.iso;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.MotionEvent;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class IsoGridViewTest {
    private IsoGridView view;
    private IsoCamera camera;
    private IsoGrid grid;
    private IsoGestures gestures;

    @Before
    public void setUp() {
        view = new IsoGridView(RuntimeEnvironment.getApplication());
        camera = new IsoCamera();
        grid = new IsoGrid();
        gestures = new IsoGestures(camera);
        view.layout(0, 0, 800, 480);
    }

    @Test
    public void unboundViewDoesNotConsumeTouch() {
        MotionEvent down = MotionEvent.obtain(1L, 2L, MotionEvent.ACTION_DOWN, 10.0f, 10.0f, 0);
        try {
            assertFalse(view.onTouchEvent(down));
        } finally {
            down.recycle();
        }
        assertNull(view.camera());
    }

    @Test
    public void boundVisibleViewConsumesDragAndDraws() {
        view.bind(camera, grid, gestures);
        view.setVisibility(View.VISIBLE);
        assertNotNull(view.camera());
        assertEquals(800.0f, camera.viewportWidth(), 0.01f);
        assertEquals(480.0f, camera.viewportHeight(), 0.01f);

        MotionEvent down = MotionEvent.obtain(1L, 2L, MotionEvent.ACTION_DOWN, 400.0f, 240.0f, 0);
        MotionEvent move = MotionEvent.obtain(1L, 16L, MotionEvent.ACTION_MOVE, 460.0f, 240.0f, 0);
        try {
            assertTrue(view.onTouchEvent(down));
            assertTrue(view.onTouchEvent(move));
        } finally {
            down.recycle();
            move.recycle();
        }
        view.draw(new android.graphics.Canvas());
        assertTrue(Math.abs(camera.lookAtX() - 16.0f) > 0.01f
                || Math.abs(camera.lookAtZ() - 16.0f) > 0.01f);
    }
}
