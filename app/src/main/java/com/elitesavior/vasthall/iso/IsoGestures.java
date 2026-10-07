package com.elitesavior.vasthall.iso;

/**
 * Mode-local pointer machine for Iso Sandbox: one finger pans, two fingers
 * pinch-zoom. Does not use StickView or the Flat pad.
 */
public final class IsoGestures {
    public static final float KEY_ZOOM_FACTOR = 1.15f;

    private static final int MAX_POINTERS = 8;

    private final IsoCamera camera;
    private final int[] ids = new int[MAX_POINTERS];
    private final float[] xs = new float[MAX_POINTERS];
    private final float[] ys = new float[MAX_POINTERS];
    private int count;
    private float pinchSpan;
    private float pinchMidX;
    private float pinchMidY;
    private boolean pinching;

    public IsoGestures(IsoCamera camera) {
        if (camera == null) {
            throw new IllegalArgumentException("camera");
        }
        this.camera = camera;
        clearPointers();
    }

    public IsoCamera camera() {
        return camera;
    }

    public void pointerDown(int id, float x, float y) {
        int index = indexOf(id);
        if (index < 0) {
            if (count >= MAX_POINTERS) {
                return;
            }
            index = count++;
            ids[index] = id;
        }
        xs[index] = x;
        ys[index] = y;
        refreshPinchAnchor();
    }

    public void pointerMove(int id, float x, float y) {
        int index = indexOf(id);
        if (index < 0) {
            return;
        }
        float prevX = xs[index];
        float prevY = ys[index];
        xs[index] = x;
        ys[index] = y;
        if (count >= 2) {
            applyPinch();
            return;
        }
        if (count == 1 && !pinching) {
            camera.panByScreen(prevX, prevY, x, y);
        }
    }

    public void pointerUp(int id) {
        int index = indexOf(id);
        if (index < 0) {
            return;
        }
        int last = count - 1;
        ids[index] = ids[last];
        xs[index] = xs[last];
        ys[index] = ys[last];
        count--;
        refreshPinchAnchor();
    }

    public void cancel() {
        clearPointers();
    }

    public void zoomIn() {
        camera.zoomBy(KEY_ZOOM_FACTOR);
    }

    public void zoomOut() {
        camera.zoomBy(1.0f / KEY_ZOOM_FACTOR);
    }

    public int pointerCount() {
        return count;
    }

    private void applyPinch() {
        if (count < 2) {
            return;
        }
        float span = span();
        float midX = (xs[0] + xs[1]) * 0.5f;
        float midY = (ys[0] + ys[1]) * 0.5f;
        if (pinching && pinchSpan > 0.5f && span > 0.5f) {
            camera.zoomBy(span / pinchSpan, pinchMidX, pinchMidY);
        }
        pinchSpan = span;
        pinchMidX = midX;
        pinchMidY = midY;
        pinching = true;
    }

    private void refreshPinchAnchor() {
        if (count >= 2) {
            pinchSpan = span();
            pinchMidX = (xs[0] + xs[1]) * 0.5f;
            pinchMidY = (ys[0] + ys[1]) * 0.5f;
            pinching = true;
        } else {
            pinching = false;
            pinchSpan = 0.0f;
        }
    }

    private float span() {
        float dx = xs[0] - xs[1];
        float dy = ys[0] - ys[1];
        return (float) Math.hypot(dx, dy);
    }

    private int indexOf(int id) {
        for (int i = 0; i < count; i++) {
            if (ids[i] == id) {
                return i;
            }
        }
        return -1;
    }

    private void clearPointers() {
        count = 0;
        pinching = false;
        pinchSpan = 0.0f;
    }
}
