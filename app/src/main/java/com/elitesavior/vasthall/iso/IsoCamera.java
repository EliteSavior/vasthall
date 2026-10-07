package com.elitesavior.vasthall.iso;

/**
 * Fixed true-isometric orthographic camera. Look-at lives on the ground
 * plane (Y=0). Zoom is a uniform scale. Native Hall rendering is unchanged;
 * this camera only drives the Iso Sandbox overlay.
 */
public final class IsoCamera {
    public static final float MIN_ZOOM = 0.35f;
    public static final float MAX_ZOOM = 3.0f;
    public static final float DEFAULT_ZOOM = 1.0f;
    public static final float PIXELS_PER_UNIT = 48.0f;
    public static final float COS30 = (float) Math.sqrt(3.0) / 2.0f;
    public static final float SIN30 = 0.5f;

    private float lookAtX = 16.0f;
    private float lookAtZ = 16.0f;
    private float zoom = DEFAULT_ZOOM;
    private float viewportWidth = 1.0f;
    private float viewportHeight = 1.0f;
    private float panMinX = -8.0f;
    private float panMaxX = 40.0f;
    private float panMinZ = -8.0f;
    private float panMaxZ = 40.0f;

    public void setViewport(float width, float height) {
        viewportWidth = Math.max(1.0f, width);
        viewportHeight = Math.max(1.0f, height);
    }

    public float viewportWidth() {
        return viewportWidth;
    }

    public float viewportHeight() {
        return viewportHeight;
    }

    public void setPanBounds(float minX, float maxX, float minZ, float maxZ) {
        panMinX = Math.min(minX, maxX);
        panMaxX = Math.max(minX, maxX);
        panMinZ = Math.min(minZ, maxZ);
        panMaxZ = Math.max(minZ, maxZ);
        clampLookAt();
    }

    public void lookAt(float x, float z) {
        lookAtX = x;
        lookAtZ = z;
        clampLookAt();
    }

    public float lookAtX() {
        return lookAtX;
    }

    public float lookAtZ() {
        return lookAtZ;
    }

    public void setZoom(float value) {
        zoom = clamp(value, MIN_ZOOM, MAX_ZOOM);
    }

    public float zoom() {
        return zoom;
    }

    public void zoomBy(float factor) {
        zoomBy(factor, viewportWidth * 0.5f, viewportHeight * 0.5f);
    }

    public void zoomBy(float factor, float focusSx, float focusSy) {
        if (factor <= 0.0f || Float.isNaN(factor)) {
            return;
        }
        float gx = groundX(focusSx, focusSy);
        float gz = groundZ(focusSx, focusSy);
        setZoom(zoom * factor);
        float gxAfter = groundX(focusSx, focusSy);
        float gzAfter = groundZ(focusSx, focusSy);
        lookAtX += gx - gxAfter;
        lookAtZ += gz - gzAfter;
        clampLookAt();
    }

    public float screenX(float worldX, float worldY, float worldZ) {
        return viewportWidth * 0.5f
                + (isoX(worldX, worldY, worldZ) - lookIsoX()) * pixels();
    }

    public float screenY(float worldX, float worldY, float worldZ) {
        return viewportHeight * 0.5f
                - (isoY(worldX, worldY, worldZ) - lookIsoY()) * pixels();
    }

    public float groundX(float screenX, float screenY) {
        float isoX = (screenX - viewportWidth * 0.5f) / pixels() + lookIsoX();
        float isoY = (viewportHeight * 0.5f - screenY) / pixels() + lookIsoY();
        float a = isoX / COS30;
        float b = isoY / SIN30;
        return (a + b) * 0.5f;
    }

    public float groundZ(float screenX, float screenY) {
        float isoX = (screenX - viewportWidth * 0.5f) / pixels() + lookIsoX();
        float isoY = (viewportHeight * 0.5f - screenY) / pixels() + lookIsoY();
        float a = isoX / COS30;
        float b = isoY / SIN30;
        return (b - a) * 0.5f;
    }

    public void panByScreen(float prevSx, float prevSy, float sx, float sy) {
        float gx = groundX(prevSx, prevSy);
        float gz = groundZ(prevSx, prevSy);
        float gxAfter = groundX(sx, sy);
        float gzAfter = groundZ(sx, sy);
        lookAtX += gx - gxAfter;
        lookAtZ += gz - gzAfter;
        clampLookAt();
    }

    private float pixels() {
        return PIXELS_PER_UNIT * zoom;
    }

    private float lookIsoX() {
        return isoX(lookAtX, 0.0f, lookAtZ);
    }

    private float lookIsoY() {
        return isoY(lookAtX, 0.0f, lookAtZ);
    }

    static float isoX(float x, float y, float z) {
        return (x - z) * COS30;
    }

    static float isoY(float x, float y, float z) {
        return (x + z) * SIN30 - y;
    }

    private void clampLookAt() {
        lookAtX = clamp(lookAtX, panMinX, panMaxX);
        lookAtZ = clamp(lookAtZ, panMinZ, panMaxZ);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
