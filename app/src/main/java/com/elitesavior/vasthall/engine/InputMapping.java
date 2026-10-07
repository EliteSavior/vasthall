package com.elitesavior.vasthall.engine;

/**
 * One key / touch / gamepad stub mapped onto a named {@link InputAction}.
 * For digital keys driving an axis, {@link #axis()} and {@link #scale()}
 * say which component they contribute.
 */
public final class InputMapping {
    public enum Axis {
        NONE,
        X,
        Y
    }

    private final String action;
    private final String key;
    private final Axis axis;
    private final float scale;

    public InputMapping(String action, String key) {
        this(action, key, Axis.NONE, 1.0f);
    }

    public InputMapping(String action, String key, Axis axis, float scale) {
        if (action == null || action.trim().isEmpty()) {
            throw new IllegalArgumentException("mapping action");
        }
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("mapping key");
        }
        if (Float.isNaN(scale) || Float.isInfinite(scale)) {
            throw new IllegalArgumentException("mapping scale");
        }
        this.action = action.trim();
        this.key = key.trim();
        this.axis = axis == null ? Axis.NONE : axis;
        this.scale = scale;
    }

    public String action() {
        return action;
    }

    public String key() {
        return key;
    }

    public Axis axis() {
        return axis;
    }

    public float scale() {
        return scale;
    }
}
