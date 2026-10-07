package com.elitesavior.vasthall.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stable key names used by {@link InputMappingContext}. Android key codes
 * map here so GameMode / PlayerController never see {@code KeyEvent} ints.
 */
public final class InputKeys {
    public static final String SPACE = "Space";
    public static final String W = "W";
    public static final String A = "A";
    public static final String S = "S";
    public static final String D = "D";
    public static final String ARROW_UP = "ArrowUp";
    public static final String ARROW_DOWN = "ArrowDown";
    public static final String ARROW_LEFT = "ArrowLeft";
    public static final String ARROW_RIGHT = "ArrowRight";
    public static final String TOUCH_JUMP = "Touch.Jump";
    public static final String TOUCH_MOVE = "Touch.Move";
    public static final String TOUCH_LOOK = "Touch.Look";
    public static final String GAMEPAD_FACE_BOTTOM = "Gamepad.FaceButtonBottom";
    public static final String GAMEPAD_LEFT_STICK = "Gamepad.LeftStick";
    public static final String GAMEPAD_RIGHT_STICK = "Gamepad.RightStick";

    // android.view.KeyEvent constants — kept as ints so the engine package
    // does not import Android types.
    static final int KEYCODE_DPAD_UP = 19;
    static final int KEYCODE_DPAD_DOWN = 20;
    static final int KEYCODE_DPAD_LEFT = 21;
    static final int KEYCODE_DPAD_RIGHT = 22;
    static final int KEYCODE_A = 29;
    static final int KEYCODE_D = 32;
    static final int KEYCODE_S = 47;
    static final int KEYCODE_W = 51;
    static final int KEYCODE_SPACE = 62;
    static final int KEYCODE_BUTTON_A = 96;

    private static final Map<Integer, String> KEY_CODES = buildKeyCodes();

    private InputKeys() {
    }

    /** Null when the key is not part of the default mapping table. */
    public static String fromKeyCode(int keyCode) {
        return KEY_CODES.get(keyCode);
    }

    static Map<Integer, String> keyCodes() {
        return KEY_CODES;
    }

    private static Map<Integer, String> buildKeyCodes() {
        Map<Integer, String> map = new LinkedHashMap<>();
        map.put(KEYCODE_SPACE, SPACE);
        map.put(KEYCODE_W, W);
        map.put(KEYCODE_A, A);
        map.put(KEYCODE_S, S);
        map.put(KEYCODE_D, D);
        map.put(KEYCODE_DPAD_UP, ARROW_UP);
        map.put(KEYCODE_DPAD_DOWN, ARROW_DOWN);
        map.put(KEYCODE_DPAD_LEFT, ARROW_LEFT);
        map.put(KEYCODE_DPAD_RIGHT, ARROW_RIGHT);
        map.put(KEYCODE_BUTTON_A, GAMEPAD_FACE_BOTTOM);
        return Collections.unmodifiableMap(map);
    }
}
