package com.elitesavior.vasthall;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

public final class DeveloperConsoleUiTest {
    @Test
    public void fossReleaseHidesConsoleHud() {
        assertFalse(DeveloperConsoleGate.UI_ENABLED);
    }
}
