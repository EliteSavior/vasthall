package com.elitesavior.vasthall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class PlayInputMachineTest {
    private PlayInputMachine machine;
    private final List<String> log = new ArrayList<>();

    @Before
    public void setUp() {
        log.clear();
        machine = new PlayInputMachine();
        machine.setSink(new PlayInputMachine.Sink() {
            @Override
            public void onBegin(PlayInputMachine.Target target, int pointerId) {
                log.add("begin " + target + " ptr=" + pointerId);
            }

            @Override
            public void onEnd(
                    PlayInputMachine.Target target, int pointerId, String reason) {
                log.add("end " + target + " ptr=" + pointerId + " reason=" + reason);
            }

            @Override
            public void onKey(int keyCode, boolean down) {
                log.add("key " + keyCode + (down ? " down" : " up"));
            }
        });
    }

    @Test
    public void liftThenMoveDoesNotReviveTheStick() {
        assertTrue(machine.pointerDown(7, PlayInputMachine.Target.MOVE));
        assertEquals(PlayInputMachine.Target.MOVE, machine.pointerMove(7));
        assertEquals(PlayInputMachine.Target.MOVE, machine.pointerUp(7, "UP"));
        assertFalse(machine.hasPointer(7));
        assertEquals(PlayInputMachine.Target.NONE, machine.pointerMove(7));
        assertFalse(logContains("begin MOVE ptr=7", 2));
        assertEquals(PlayInputMachine.Target.NONE, machine.targetOf(7));
    }

    @Test
    public void occupiedStickIgnoresASecondFinger() {
        assertTrue(machine.pointerDown(1, PlayInputMachine.Target.MOVE));
        assertFalse(machine.pointerDown(2, PlayInputMachine.Target.MOVE));
        assertEquals(1, machine.ownerOf(PlayInputMachine.Target.MOVE));
        assertFalse(machine.hasPointer(2));
    }

    @Test
    public void twoTargetsReleaseIndependently() {
        assertTrue(machine.pointerDown(7, PlayInputMachine.Target.MOVE));
        assertTrue(machine.pointerDown(11, PlayInputMachine.Target.LOOK));
        assertEquals(PlayInputMachine.Target.MOVE, machine.pointerUp(7, "UP"));
        assertEquals(PlayInputMachine.Target.LOOK, machine.pointerMove(11));
        assertFalse(machine.hasPointer(7));
        assertEquals(PlayInputMachine.Target.NONE, machine.pointerUp(7, "UP"));
        assertEquals(PlayInputMachine.Target.LOOK, machine.pointerUp(11, "UP"));
    }

    @Test
    public void jumpIsOwnedByThePointerThatPressedIt() {
        assertTrue(machine.pointerDown(3, PlayInputMachine.Target.JUMP));
        assertFalse(machine.pointerDown(4, PlayInputMachine.Target.JUMP));
        assertEquals(PlayInputMachine.Target.NONE, machine.pointerUp(4, "UP"));
        assertTrue(machine.hasPointer(3));
        assertEquals(PlayInputMachine.Target.JUMP, machine.pointerUp(3, "UP"));
        assertTrue(log.contains("end JUMP ptr=3 reason=UP"));
        assertFalse(logContains("end JUMP", 2));
    }

    @Test
    public void keyRepeatDoesNotRetriggerAndReleaseAllSendsKeyUp() {
        assertTrue(machine.keyDown(51));
        assertFalse(machine.keyDown(51));
        assertTrue(machine.isKeyDown(51));
        machine.releaseAll("PAUSE");
        assertFalse(machine.isKeyDown(51));
        assertTrue(log.contains("key 51 down"));
        assertTrue(log.contains("key 51 up"));
        assertEquals(1, countPrefix("key 51 down"));
        assertEquals(1, countPrefix("key 51 up"));
    }

    @Test
    public void releaseAllEndsPointersAndKeysTogether() {
        machine.pointerDown(7, PlayInputMachine.Target.MOVE);
        machine.pointerDown(11, PlayInputMachine.Target.LOOK);
        machine.pointerDown(3, PlayInputMachine.Target.JUMP);
        machine.keyDown(32);
        machine.releaseAll("FOCUS_LOST");
        assertFalse(machine.anyPressed());
        assertTrue(log.contains("end MOVE ptr=7 reason=FOCUS_LOST"));
        assertTrue(log.contains("end LOOK ptr=11 reason=FOCUS_LOST"));
        assertTrue(log.contains("end JUMP ptr=3 reason=FOCUS_LOST"));
        assertTrue(log.contains("key 32 up"));
    }

    @Test
    public void unboundPointerUpIsIgnored() {
        assertEquals(PlayInputMachine.Target.NONE, machine.pointerUp(9, "UP"));
        assertTrue(log.isEmpty());
    }

    @Test
    public void legacyAllowsMultiplePointers() {
        assertTrue(machine.pointerDown(1, PlayInputMachine.Target.LEGACY));
        assertTrue(machine.pointerDown(2, PlayInputMachine.Target.LEGACY));
        assertEquals(PlayInputMachine.Target.LEGACY, machine.pointerUp(1, "UP"));
        assertTrue(machine.hasPointer(2));
    }

    private boolean logContains(String needle, int minCount) {
        return countPrefix(needle) >= minCount;
    }

    private int countPrefix(String needle) {
        int n = 0;
        for (String line : log) {
            if (line.equals(needle) || line.startsWith(needle)) {
                n++;
            }
        }
        return n;
    }
}
