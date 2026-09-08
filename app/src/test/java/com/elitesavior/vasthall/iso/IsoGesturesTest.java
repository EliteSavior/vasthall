package com.elitesavior.vasthall.iso;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public final class IsoGesturesTest {
    private IsoCamera camera;
    private IsoGestures gestures;

    @Before
    public void setUp() {
        camera = new IsoCamera();
        camera.setViewport(800.0f, 480.0f);
        camera.lookAt(16.0f, 16.0f);
        camera.setZoom(1.0f);
        camera.setPanBounds(-8.0f, 40.0f, -8.0f, 40.0f);
        gestures = new IsoGestures(camera);
    }

    @Test
    public void oneFingerDragPansLookAt() {
        float lookX = camera.lookAtX();
        float lookZ = camera.lookAtZ();
        gestures.pointerDown(1, 400.0f, 240.0f);
        gestures.pointerMove(1, 460.0f, 240.0f);
        assertTrue(Math.abs(camera.lookAtX() - lookX) > 0.01f
                || Math.abs(camera.lookAtZ() - lookZ) > 0.01f);
    }

    @Test
    public void pinchOutZoomsIn() {
        gestures.pointerDown(1, 350.0f, 240.0f);
        gestures.pointerDown(2, 450.0f, 240.0f);
        gestures.pointerMove(1, 300.0f, 240.0f);
        gestures.pointerMove(2, 500.0f, 240.0f);
        assertTrue(camera.zoom() > 1.0f);
        assertTrue(camera.zoom() <= IsoCamera.MAX_ZOOM);
    }

    @Test
    public void pinchZoomIsClamped() {
        camera.setZoom(IsoCamera.MAX_ZOOM);
        gestures.pointerDown(1, 350.0f, 240.0f);
        gestures.pointerDown(2, 450.0f, 240.0f);
        gestures.pointerMove(1, 200.0f, 240.0f);
        gestures.pointerMove(2, 600.0f, 240.0f);
        assertEquals(IsoCamera.MAX_ZOOM, camera.zoom(), 0.0001f);
    }

    @Test
    public void plusMinusKeysZoomAndClamp() {
        float before = camera.zoom();
        gestures.zoomIn();
        assertTrue(camera.zoom() > before);
        camera.setZoom(IsoCamera.MAX_ZOOM);
        gestures.zoomIn();
        assertEquals(IsoCamera.MAX_ZOOM, camera.zoom(), 0.0001f);
        camera.setZoom(IsoCamera.MIN_ZOOM);
        gestures.zoomOut();
        assertEquals(IsoCamera.MIN_ZOOM, camera.zoom(), 0.0001f);
    }

    @Test
    public void releasingSecondFingerDoesNotJumpLookAt() {
        gestures.pointerDown(1, 400.0f, 240.0f);
        gestures.pointerDown(2, 500.0f, 240.0f);
        gestures.pointerUp(2);
        float lookX = camera.lookAtX();
        float lookZ = camera.lookAtZ();
        gestures.pointerMove(1, 400.0f, 240.0f);
        assertEquals(lookX, camera.lookAtX(), 0.0001f);
        assertEquals(lookZ, camera.lookAtZ(), 0.0001f);
    }
}
