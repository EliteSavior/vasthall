package com.elitesavior.vasthall;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import android.view.MotionEvent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class DebugHubTest {
    private DebugHub hub;
    private StickView left;
    private StickView right;

    @Before
    public void setUp() {
        SharedPreferences prefs = RuntimeEnvironment.getApplication()
                .getSharedPreferences("debug_test", 0);
        prefs.edit().clear().apply();
        hub = new DebugHub(prefs);
        hub.setOn(true);
        left = new StickView(RuntimeEnvironment.getApplication(), true);
        right = new StickView(RuntimeEnvironment.getApplication(), false);
        left.layout(0, 0, 500, 500);
        right.layout(500, 0, 1000, 500);
    }

    @Test
    public void dumpHasStableHeadersAndCameraConstantsFromNative() {
        left.onTouchEvent(event(1, MotionEvent.ACTION_DOWN, 100.0f, 200.0f));
        left.onTouchEvent(event(1, MotionEvent.ACTION_MOVE, 180.0f, 200.0f));
        hub.setNativeSize(1920, 1080);
        hub.setSurfaceSize(1920, 1080);
        hub.setOrient("landscape");
        hub.setPaused(false);
        hub.lifecycle("onResume");
        hub.onZero("left", "CANCEL", 1, 1.0f, 0.0f);

        hub.onTouch("left", event(1, MotionEvent.ACTION_DOWN, 100.0f, 200.0f),
                null, left.axisX(), left.axisY(), 0.0f, 0.0f);
        hub.onTouch("left", event(1, MotionEvent.ACTION_MOVE, 180.0f, 200.0f),
                null, left.axisX(), left.axisY(), 0.0f, 0.0f);
        hub.onTouch("left", event(1, MotionEvent.ACTION_UP, 180.0f, 200.0f),
                "UP", 0.0f, 0.0f, 0.0f, 0.0f);

        String dump = hub.buildDump("0.16.0", "dual", left, right, false);
        assertTrue(dump.startsWith("VASTHALL_DEBUG v1\n"));
        assertTrue(dump.contains("package=com.elitesavior.vasthall"));
        assertTrue(dump.contains("debug=on"));
        assertTrue(dump.contains("[CONTROLS]"));
        assertTrue(dump.contains("scheme=dual"));
        assertTrue(dump.contains("left.active=1"));
        assertTrue(dump.contains("stuckHint="));
        assertTrue(dump.contains("[CAMERA]"));
        assertTrue(dump.contains("lookSens=2.3500"));
        assertTrue(dump.contains("invertY=0"));
        assertTrue(dump.contains("pitchMin=-1.5000"));
        assertTrue(dump.contains("pitchMax=1.5000"));
        assertFalse(dump.contains("pitchMin=unset_camera"));
        assertTrue(dump.contains("jniLagMs="));
        assertTrue(dump.contains("jniLagMaxMs="));
        assertTrue(dump.contains("lookDeadzone=0.1200"));
        assertTrue(dump.contains("[INPUT_LOG]"));
        assertTrue(dump.contains("action=DOWN"));
        assertTrue(dump.contains("action=MOVE"));
        assertTrue(dump.contains("action=UP"));
        assertTrue(dump.contains("[ENGINE]"));
        assertTrue(dump.contains("nativeResize=1920,1080"));
        assertTrue(dump.contains("orient=landscape"));
        assertTrue(dump.contains("[LIFECYCLE]"));
        assertTrue(dump.contains("onResume"));
        assertTrue(dump.contains("[LOCKUP]"));
        assertTrue(dump.contains("whoZeroed=CANCEL"));
        assertFalse(dump.contains("INTERNET"));
    }

    @Test
    public void masterOnForcesInputLogEvenIfSubsystemWasOff() {
        hub.setSubsystem(DebugHub.PREF_INPUT, false);
        hub.setSubsystem(DebugHub.PREF_LIFECYCLE, false);
        hub.setOn(true);
        hub.lifecycle("onResume");
        hub.onTouch("left", event(2, MotionEvent.ACTION_DOWN, 10.0f, 10.0f),
                null, 0.0f, 0.0f, 0.0f, 0.0f);
        String dump = hub.buildDump("0.16.0", "dual", left, right, false);
        assertTrue(dump.contains("action=DOWN"));
        assertTrue(dump.contains("jniLagMs="));
        assertTrue(dump.contains("onResume"));
        assertFalse(dump.contains("[INPUT_LOG]\n(empty)"));
    }

    @Test
    public void clampDtCapsHitches() {
        org.junit.Assert.assertEquals(0.001f, DebugHub.clampDt(0.0f), 0.00001f);
        org.junit.Assert.assertEquals(0.033f, DebugHub.clampDt(0.25f), 0.00001f);
        org.junit.Assert.assertEquals(0.016f, DebugHub.clampDt(0.016f), 0.00001f);
    }

    @Test
    public void inputLogIncludesJniLagMs() {
        hub.setJniLagMs(12);
        hub.onTouch("right", event(3, MotionEvent.ACTION_MOVE, 10.0f, 10.0f),
                null, 0.0f, 0.0f, 0.4f, -0.2f);
        String dump = hub.buildDump("0.16.0", "dual", left, right, false);
        assertTrue(dump.contains("jniLagMs=12"));
        assertTrue(dump.contains("action=MOVE"));
        org.junit.Assert.assertEquals(12L, hub.jniLagMs());
    }

    @Test
    public void subsystemOffSkipsBlocksWhileMasterOn() {
        hub.setSubsystem(DebugHub.PREF_CAMERA, false);
        String dump = hub.buildDump("0.16.0", "dual", left, right, false);
        assertTrue(dump.contains("[CAMERA]"));
        assertTrue(dump.contains("skipped=off"));
        assertTrue(dump.contains("[CONTROLS]"));
        assertTrue(dump.contains("scheme=dual"));
    }

    private static MotionEvent event(int pointerId, int action, float x, float y) {
        android.view.MotionEvent.PointerProperties[] properties =
                new android.view.MotionEvent.PointerProperties[1];
        android.view.MotionEvent.PointerCoords[] coords =
                new android.view.MotionEvent.PointerCoords[1];
        properties[0] = new android.view.MotionEvent.PointerProperties();
        properties[0].id = pointerId;
        properties[0].toolType = MotionEvent.TOOL_TYPE_FINGER;
        coords[0] = new android.view.MotionEvent.PointerCoords();
        coords[0].x = x;
        coords[0].y = y;
        coords[0].pressure = 1.0f;
        coords[0].size = 1.0f;
        return MotionEvent.obtain(
                1L, 2L, action, 1, properties, coords,
                0, 0, 1.0f, 1.0f, 0, 0,
                android.view.InputDevice.SOURCE_TOUCHSCREEN, 0);
    }
}
