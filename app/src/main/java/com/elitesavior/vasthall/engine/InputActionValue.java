package com.elitesavior.vasthall.engine;

/**
 * Current value of an {@link InputAction}. Digital actions use
 * {@link #isPressed()}; axis actions use {@link #x()} / {@link #y()}.
 */
public final class InputActionValue {
    private final InputValueType valueType;
    private final boolean pressed;
    private final float x;
    private final float y;

    private InputActionValue(InputValueType valueType, boolean pressed, float x, float y) {
        this.valueType = valueType;
        this.pressed = pressed;
        this.x = x;
        this.y = y;
    }

    public static InputActionValue digital(boolean down) {
        return new InputActionValue(InputValueType.DIGITAL, down, down ? 1.0f : 0.0f, 0.0f);
    }

    public static InputActionValue axis1D(float value) {
        return new InputActionValue(InputValueType.AXIS1D, value != 0.0f, value, 0.0f);
    }

    public static InputActionValue axis2D(float x, float y) {
        return new InputActionValue(InputValueType.AXIS2D, x != 0.0f || y != 0.0f, x, y);
    }

    public static InputActionValue zero(InputValueType type) {
        if (type == InputValueType.DIGITAL) {
            return digital(false);
        }
        if (type == InputValueType.AXIS1D) {
            return axis1D(0.0f);
        }
        return axis2D(0.0f, 0.0f);
    }

    public InputValueType valueType() {
        return valueType;
    }

    public boolean isPressed() {
        return pressed;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float axis1D() {
        return x;
    }

    public boolean isZero() {
        return !pressed && x == 0.0f && y == 0.0f;
    }
}
