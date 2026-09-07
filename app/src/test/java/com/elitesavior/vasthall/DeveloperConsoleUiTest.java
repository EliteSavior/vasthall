package com.elitesavior.vasthall;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DeveloperConsoleUiTest {
    @Test
    public void fossDebugExposesConsoleHud() {
        // Do not touch VastHallActivity: its static block loads libvasthall.so.
        assertTrue(DeveloperConsoleGate.UI_ENABLED);
    }
}
