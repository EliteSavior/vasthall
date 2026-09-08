package com.elitesavior.vasthall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public final class FlatPadRouterTest {
    private static final float EPSILON = 0.0001f;

    private FlatPadRouter router;
    private RecordingSink sink;

    @Before
    public void setUp() {
        sink = new RecordingSink();
        router = new FlatPadRouter();
        router.setSink(sink);
        router.setLayout(playLayout());
    }

    @Test
    public void downBindsMoveThenMoveUpdatesAxesThenUpClears() {
        assertTrue(router.down(7, 100.0f, 200.0f));
        assertEquals(FlatPadRouter.Target.MOVE, router.targetOf(7));
        assertEquals(0.0f, router.moveX(), EPSILON);
        assertEquals(0.0f, router.moveY(), EPSILON);

        assertEquals(FlatPadRouter.Target.MOVE, router.move(7, 171.0f, 200.0f));
        assertEquals(1.0f, router.moveX(), EPSILON);
        assertEquals(0.0f, router.moveY(), EPSILON);
        assertEquals(1.0f, sink.moveX, EPSILON);

        assertEquals(FlatPadRouter.Target.MOVE, router.up(7, "UP"));
        assertFalse(router.hasPointer(7));
        assertEquals(FlatPadRouter.Target.NONE, router.targetOf(7));
        assertEquals(0.0f, router.moveX(), EPSILON);
        assertEquals(0.0f, router.moveY(), EPSILON);
        assertEquals(0.0f, sink.moveX, EPSILON);
    }

    @Test
    public void unboundMoveIsIgnoredAndDoesNotReviveAfterUp() {
        assertEquals(FlatPadRouter.Target.NONE, router.move(7, 140.0f, 200.0f));
        assertEquals(0.0f, router.moveX(), EPSILON);
        assertFalse(router.hasPointer(7));

        assertTrue(router.down(7, 100.0f, 200.0f));
        router.move(7, 171.0f, 200.0f);
        assertEquals(1.0f, router.moveX(), EPSILON);
        router.up(7, "UP");
        assertEquals(0.0f, router.moveX(), EPSILON);

        assertEquals(FlatPadRouter.Target.NONE, router.move(7, 171.0f, 200.0f));
        assertFalse(router.hasPointer(7));
        assertEquals(0.0f, router.moveX(), EPSILON);
        assertEquals(0.0f, sink.moveX, EPSILON);
    }

    @Test
    public void jumpDownDoesNotStealAnActiveLookBind() {
        assertTrue(router.down(11, 700.0f, 200.0f));
        assertEquals(FlatPadRouter.Target.LOOK, router.targetOf(11));
        router.move(11, 771.0f, 200.0f);
        assertEquals(1.0f, router.lookX(), EPSILON);

        assertTrue(router.down(3, 820.0f, 500.0f));
        assertEquals(FlatPadRouter.Target.JUMP, router.targetOf(3));
        assertTrue(router.jumpDown());
        assertEquals(FlatPadRouter.Target.LOOK, router.targetOf(11));
        assertEquals(1.0f, router.lookX(), EPSILON);

        router.move(11, 771.0f, 520.0f);
        assertEquals(FlatPadRouter.Target.LOOK, router.targetOf(11));
        assertFalse(router.jumpDown() && router.targetOf(11) == FlatPadRouter.Target.JUMP);
        assertEquals(FlatPadRouter.Target.JUMP, router.targetOf(3));
    }

    @Test
    public void cancelClearsEveryBoundPointerAndZerosOutputs() {
        router.down(7, 100.0f, 200.0f);
        router.move(7, 171.0f, 200.0f);
        router.down(11, 700.0f, 200.0f);
        router.move(11, 771.0f, 200.0f);
        router.down(3, 820.0f, 500.0f);
        assertTrue(router.anyPressed());

        router.releaseAll("CANCEL");
        assertFalse(router.anyPressed());
        assertFalse(router.hasPointer(7));
        assertFalse(router.hasPointer(11));
        assertFalse(router.hasPointer(3));
        assertEquals(0.0f, router.moveX(), EPSILON);
        assertEquals(0.0f, router.lookX(), EPSILON);
        assertFalse(router.jumpDown());
        assertEquals(0.0f, sink.moveX, EPSILON);
        assertEquals(0.0f, sink.lookX, EPSILON);
        assertFalse(sink.jump);
    }

    @Test
    public void schemeSwitchReleasesThenEnablesOnlyTheSelectedPath() {
        ControlSchemeGate gate = new ControlSchemeGate(ControlScheme.NEW_PAD);
        router.down(7, 100.0f, 200.0f);
        router.move(7, 171.0f, 200.0f);
        assertTrue(router.hasPointer(7));
        assertTrue(gate.newPadEnabled());
        assertFalse(gate.legacyTouchEnabled());
        assertFalse(gate.legacyPadEnabled());

        gate.select(ControlScheme.LEGACY_TOUCH, () -> router.releaseAll("APPLY_SCHEME"));
        assertFalse(router.anyPressed());
        assertEquals(0.0f, router.moveX(), EPSILON);
        assertTrue(gate.legacyTouchEnabled());
        assertFalse(gate.legacyPadEnabled());
        assertFalse(gate.newPadEnabled());
        assertEquals(ControlScheme.LEGACY_TOUCH, gate.current());

        gate.select(ControlScheme.LEGACY_PAD, () -> router.releaseAll("APPLY_SCHEME"));
        assertTrue(gate.legacyPadEnabled());
        assertFalse(gate.newPadEnabled());
        assertFalse(gate.legacyTouchEnabled());
    }

    @Test
    public void settingsExposeExactlyLegacyTouchLegacyPadAndNewPad() {
        ControlScheme[] options = ControlScheme.settingsOrder();
        assertEquals(3, options.length);
        assertEquals("Legacy touch", options[0].label());
        assertEquals("Legacy pad", options[1].label());
        assertEquals("New pad", options[2].label());
        assertEquals(ControlScheme.LEGACY_TOUCH, ControlScheme.fromPref("legacy"));
        assertEquals(ControlScheme.LEGACY_PAD, ControlScheme.fromPref("dual"));
        assertEquals(ControlScheme.NEW_PAD, ControlScheme.fromPref("flat"));
        assertEquals(ControlScheme.LEGACY_PAD, ControlScheme.fromPref(null));
        assertEquals("legacy", ControlScheme.LEGACY_TOUCH.prefValue());
        assertEquals("dual", ControlScheme.LEGACY_PAD.prefValue());
        assertEquals("flat", ControlScheme.NEW_PAD.prefValue());
    }

    private static FlatPadRouter.Layout playLayout() {
        FlatPadRouter.Layout layout = new FlatPadRouter.Layout();
        layout.width = 1000.0f;
        layout.height = 600.0f;
        layout.stickRadius = 71.0f;
        layout.jumpLeft = 800.0f;
        layout.jumpTop = 480.0f;
        layout.jumpRight = 872.0f;
        layout.jumpBottom = 552.0f;
        return layout;
    }

    private static final class RecordingSink implements FlatPadRouter.Sink {
        float moveX;
        float moveY;
        float lookX;
        float lookY;
        boolean jump;

        @Override
        public void setMove(float x, float y) {
            moveX = x;
            moveY = y;
        }

        @Override
        public void setLook(float x, float y) {
            lookX = x;
            lookY = y;
        }

        @Override
        public void setJump(boolean down) {
            jump = down;
        }
    }
}
