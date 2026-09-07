package com.elitesavior.vasthall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.elitesavior.vasthall.engine.TextWidget;
import com.elitesavior.vasthall.engine.WidgetViewport;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class WidgetOverlayTest {
    private WidgetOverlay overlay;
    private WidgetViewport viewport;

    @Before
    public void setUp() {
        overlay = new WidgetOverlay(RuntimeEnvironment.getApplication());
        viewport = new WidgetViewport();
        overlay.bind(viewport);
    }

    @Test
    public void overlayNeverConsumesTouches() {
        MotionEvent down = MotionEvent.obtain(1L, 2L, MotionEvent.ACTION_DOWN, 10.0f, 10.0f, 0);
        try {
            assertFalse(overlay.dispatchTouchEvent(down));
            assertFalse(overlay.onInterceptTouchEvent(down));
            assertFalse(overlay.onTouchEvent(down));
        } finally {
            down.recycle();
        }
    }

    @Test
    public void addHideAndRemoveMirrorTheViewport() {
        TextWidget label = viewport.createWidget(TextWidget.class, "Hint");
        label.setText("Hello");
        label.addToViewport();

        assertEquals(1, overlay.getChildCount());
        TextView view = (TextView) overlay.getChildAt(0);
        assertEquals("Hello", view.getText().toString());
        assertEquals(View.VISIBLE, view.getVisibility());

        label.hide();
        assertEquals(View.GONE, view.getVisibility());

        label.removeFromParent();
        assertEquals(0, overlay.getChildCount());
    }
}
