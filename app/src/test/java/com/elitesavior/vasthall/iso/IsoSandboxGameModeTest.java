package com.elitesavior.vasthall.iso;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.elitesavior.vasthall.engine.GameInstance;
import com.elitesavior.vasthall.engine.GameModeTypes;
import com.elitesavior.vasthall.engine.HallGameMode;
import com.elitesavior.vasthall.engine.PlayerPawn;

import org.junit.Before;
import org.junit.Test;

public final class IsoSandboxGameModeTest {
    private GameInstance game;

    @Before
    public void setUp() {
        game = GameInstance.withDemoAssets();
        game.init();
    }

    @Test
    public void openIsoSandboxInstallsModeWithoutPawn() {
        game.openLevel("IsoSandbox");

        assertTrue(game.gameMode() instanceof IsoSandboxGameMode);
        IsoSandboxGameMode mode = (IsoSandboxGameMode) game.gameMode();
        assertEquals("IsoSandboxGameMode", GameModeTypes.resolve("IsoSandboxGameMode").getSimpleName());
        assertEquals(0, game.world().actorCount());
        assertNull(mode.defaultPawnClass());
        assertNull(mode.findDefaultPawn());
        assertNotNull(mode.camera());
        assertNotNull(mode.grid());
        assertNotNull(mode.gestures());
        assertEquals(32, mode.grid().size());
        assertEquals(16.0f, mode.camera().lookAtX(), 0.0001f);
        assertEquals(16.0f, mode.camera().lookAtZ(), 0.0001f);
        assertEquals(IsoSandboxGameMode.SAMPLE_WIDGET_TEXT, mode.sampleWidget().text());
    }

    @Test
    public void travelBackToHallRestoresHallGameMode() {
        game.openLevel("IsoSandbox");
        IsoSandboxGameMode iso = (IsoSandboxGameMode) game.gameMode();
        game.openLevel("Hall");
        assertTrue(game.gameMode() instanceof HallGameMode);
        assertEquals(1, iso.endCount());
        assertNotNull(game.world().findActor(PlayerPawn.DEFAULT_NAME));
        assertTrue(game.world().actorCount() >= 2);
    }
}
