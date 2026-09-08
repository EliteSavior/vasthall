package com.elitesavior.vasthall;

/**
 * Play-time control schemes. Pref values for the two older options stay
 * stable ({@code legacy}, {@code dual}) so existing installs keep their
 * choice. New pad is the Flat router only.
 */
enum ControlScheme {
    LEGACY_TOUCH("legacy", "Legacy touch"),
    LEGACY_PAD("dual", "Legacy pad"),
    NEW_PAD("flat", "New pad");

    private final String prefValue;
    private final String label;

    ControlScheme(String prefValue, String label) {
        this.prefValue = prefValue;
        this.label = label;
    }

    String prefValue() {
        return prefValue;
    }

    String label() {
        return label;
    }

    static ControlScheme fromPref(String value) {
        if (LEGACY_TOUCH.prefValue.equals(value)) {
            return LEGACY_TOUCH;
        }
        if (NEW_PAD.prefValue.equals(value)) {
            return NEW_PAD;
        }
        return LEGACY_PAD;
    }

    static ControlScheme[] settingsOrder() {
        return new ControlScheme[] {LEGACY_TOUCH, LEGACY_PAD, NEW_PAD};
    }
}
