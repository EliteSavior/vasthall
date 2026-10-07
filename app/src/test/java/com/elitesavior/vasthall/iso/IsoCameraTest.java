package com.elitesavior.vasthall.iso;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public final class IsoCameraTest {
    private IsoCamera camera;

    @Before
    public void setUp() {
        camera = new IsoCamera();
        camera.setViewport(800.0f, 480.0f);
        camera.lookAt(16.0f, 16.0f);
        camera.setZoom(1.0f);
        camera.setPanBounds(-8.0f, 40.0f, -8.0f, 40.0f);
    }

    @Test
    public void lookAtProjectsToViewportCenter() {
        assertEquals(400.0f, camera.screenX(16.0f, 0.0f, 16.0f), 0.05f);
        assertEquals(240.0f, camera.screenY(16.0f, 0.0f, 16.0f), 0.05f);
    }

    @Test
    public void groundRoundTripAtYZero() {
        float sx = camera.screenX(4.0f, 0.0f, 9.0f);
        float sy = camera.screenY(4.0f, 0.0f, 9.0f);
        assertEquals(4.0f, camera.groundX(sx, sy), 0.05f);
        assertEquals(9.0f, camera.groundZ(sx, sy), 0.05f);
    }

    @Test
    public void zoomIsClamped() {
        camera.setZoom(99.0f);
        assertEquals(IsoCamera.MAX_ZOOM, camera.zoom(), 0.0001f);
        camera.setZoom(0.01f);
        assertEquals(IsoCamera.MIN_ZOOM, camera.zoom(), 0.0001f);
        camera.zoomBy(0.01f);
        assertEquals(IsoCamera.MIN_ZOOM, camera.zoom(), 0.0001f);
        camera.setZoom(1.0f);
        camera.zoomBy(100.0f);
        assertEquals(IsoCamera.MAX_ZOOM, camera.zoom(), 0.0001f);
    }

    @Test
    public void panByScreenKeepsGrabbedGroundPoint() {
        float grabSx = 420.0f;
        float grabSy = 260.0f;
        float gx = camera.groundX(grabSx, grabSy);
        float gz = camera.groundZ(grabSx, grabSy);
        camera.panByScreen(grabSx, grabSy, grabSx + 40.0f, grabSy - 24.0f);
        assertEquals(gx, camera.groundX(grabSx + 40.0f, grabSy - 24.0f), 0.08f);
        assertEquals(gz, camera.groundZ(grabSx + 40.0f, grabSy - 24.0f), 0.08f);
    }

    @Test
    public void panClampsLookAt() {
        camera.lookAt(-100.0f, 99.0f);
        assertEquals(-8.0f, camera.lookAtX(), 0.0001f);
        assertEquals(40.0f, camera.lookAtZ(), 0.0001f);
    }

    @Test
    public void zoomAboutFocusKeepsGroundPoint() {
        float fx = 500.0f;
        float fy = 200.0f;
        float gx = camera.groundX(fx, fy);
        float gz = camera.groundZ(fx, fy);
        camera.zoomBy(1.4f, fx, fy);
        assertTrue(camera.zoom() > 1.0f);
        assertEquals(gx, camera.groundX(fx, fy), 0.12f);
        assertEquals(gz, camera.groundZ(fx, fy), 0.12f);
    }
}
