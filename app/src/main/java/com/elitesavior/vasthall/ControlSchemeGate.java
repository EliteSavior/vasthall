package com.elitesavior.vasthall;

/**
 * Runtime scheme switch. Always {@code releaseAll} first, then enable only
 * the selected path. Does not own Legacy touch or Legacy pad logic.
 */
final class ControlSchemeGate {
    private ControlScheme scheme;

    ControlSchemeGate(ControlScheme initial) {
        this.scheme = initial == null ? ControlScheme.LEGACY_PAD : initial;
    }

    ControlScheme current() {
        return scheme;
    }

    ControlScheme select(ControlScheme next, Runnable releaseAll) {
        if (releaseAll != null) {
            releaseAll.run();
        }
        scheme = next == null ? ControlScheme.LEGACY_PAD : next;
        return scheme;
    }

    boolean legacyTouchEnabled() {
        return scheme == ControlScheme.LEGACY_TOUCH;
    }

    boolean legacyPadEnabled() {
        return scheme == ControlScheme.LEGACY_PAD;
    }

    boolean newPadEnabled() {
        return scheme == ControlScheme.NEW_PAD;
    }
}
