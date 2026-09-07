package com.elitesavior.vasthall.engine;

/**
 * Named input action. Unreal analog: {@code UInputAction} — Jump, Move,
 * Look — independent of the keys or sticks that drive it.
 */
public final class InputAction {
    public static final String JUMP = "Jump";
    public static final String MOVE = "Move";
    public static final String LOOK = "Look";

    private final String name;
    private final InputValueType valueType;

    public InputAction(String name, InputValueType valueType) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("action name");
        }
        if (valueType == null) {
            throw new IllegalArgumentException("value type");
        }
        this.name = name.trim();
        this.valueType = valueType;
    }

    public String name() {
        return name;
    }

    public InputValueType valueType() {
        return valueType;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InputAction that)) {
            return false;
        }
        return name.equals(that.name) && valueType == that.valueType;
    }

    @Override
    public int hashCode() {
        return 31 * name.hashCode() + valueType.hashCode();
    }
}
