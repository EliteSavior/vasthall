package com.elitesavior.vasthall.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.elitesavior.vasthall.PlayInputRouter;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class InputActionMappingTest {
    private World world;
    private InputSubsystem input;
    private final List<String> log = new ArrayList<>();

    @Before
    public void setUp() {
        world = new World();
        input = world.input();
        log.clear();
    }

    @Test
    public void defaultContextMapsJumpMoveLook() {
        InputMappingContext defaults = InputMappingContext.defaults();
        assertEquals("Default", defaults.name());
        assertEquals(InputValueType.DIGITAL, defaults.action(InputAction.JUMP).valueType());
        assertEquals(InputValueType.AXIS2D, defaults.action(InputAction.MOVE).valueType());
        assertEquals(InputValueType.AXIS2D, defaults.action(InputAction.LOOK).valueType());
        assertTrue(defaults.maps(InputKeys.SPACE, InputAction.JUMP));
        assertTrue(defaults.maps(InputKeys.TOUCH_JUMP, InputAction.JUMP));
        assertTrue(defaults.maps(InputKeys.GAMEPAD_FACE_BOTTOM, InputAction.JUMP));
        assertTrue(defaults.maps(InputKeys.W, InputAction.MOVE));
        assertTrue(defaults.maps(InputKeys.TOUCH_MOVE, InputAction.MOVE));
        assertTrue(defaults.maps(InputKeys.GAMEPAD_LEFT_STICK, InputAction.MOVE));
        assertTrue(defaults.maps(InputKeys.TOUCH_LOOK, InputAction.LOOK));
        assertTrue(defaults.maps(InputKeys.GAMEPAD_RIGHT_STICK, InputAction.LOOK));
        assertEquals(defaults.action(InputAction.JUMP), input.action(InputAction.JUMP));
    }

    @Test
    public void spacePressAndReleaseDispatchJumpStartedTriggeredCompleted() {
        bindAll(InputAction.JUMP);

        input.injectKey(InputKeys.SPACE, true);
        assertEquals(List.of("Jump:STARTED:1", "Jump:TRIGGERED:1"), log);
        assertTrue(input.actionValue(InputAction.JUMP).isPressed());

        input.injectKey(InputKeys.SPACE, false);
        assertEquals(
                List.of("Jump:STARTED:1", "Jump:TRIGGERED:1", "Jump:COMPLETED:0"),
                log);
        assertFalse(input.actionValue(InputAction.JUMP).isPressed());
    }

    @Test
    public void touchAndGamepadStubsAlsoFireJump() {
        bindAll(InputAction.JUMP);

        input.injectKey(InputKeys.TOUCH_JUMP, true);
        input.injectKey(InputKeys.TOUCH_JUMP, false);
        input.injectKey(InputKeys.GAMEPAD_FACE_BOTTOM, true);
        input.injectKey(InputKeys.GAMEPAD_FACE_BOTTOM, false);

        assertEquals(
                List.of(
                        "Jump:STARTED:1",
                        "Jump:TRIGGERED:1",
                        "Jump:COMPLETED:0",
                        "Jump:STARTED:1",
                        "Jump:TRIGGERED:1",
                        "Jump:COMPLETED:0"),
                log);
    }

    @Test
    public void wasdAccumulatesMoveAxis() {
        bindAll(InputAction.MOVE);

        input.injectKey(InputKeys.W, true);
        assertEquals(List.of("Move:STARTED:0.00,1.00", "Move:TRIGGERED:0.00,1.00"), log);
        assertEquals(0.0f, input.actionValue(InputAction.MOVE).x(), 0.0001f);
        assertEquals(1.0f, input.actionValue(InputAction.MOVE).y(), 0.0001f);

        log.clear();
        input.injectKey(InputKeys.D, true);
        assertEquals(List.of("Move:TRIGGERED:1.00,1.00"), log);

        log.clear();
        input.injectKey(InputKeys.W, false);
        assertEquals(List.of("Move:TRIGGERED:1.00,0.00"), log);

        log.clear();
        input.injectKey(InputKeys.D, false);
        assertEquals(List.of("Move:COMPLETED:0.00,0.00"), log);
        assertEquals(0.0f, input.actionValue(InputAction.MOVE).x(), 0.0001f);
        assertEquals(0.0f, input.actionValue(InputAction.MOVE).y(), 0.0001f);
    }

    @Test
    public void touchAndGamepadAxisDispatchLook() {
        bindAll(InputAction.LOOK);

        input.injectAxis(InputKeys.TOUCH_LOOK, 0.4f, -0.25f);
        assertEquals(List.of("Look:STARTED:0.40,-0.25", "Look:TRIGGERED:0.40,-0.25"), log);
        assertEquals(0.4f, input.actionValue(InputAction.LOOK).x(), 0.0001f);
        assertEquals(-0.25f, input.actionValue(InputAction.LOOK).y(), 0.0001f);

        log.clear();
        input.injectAxis(InputKeys.GAMEPAD_RIGHT_STICK, -0.5f, 0.1f);
        assertEquals(List.of("Look:TRIGGERED:-0.10,-0.15"), log);

        log.clear();
        input.injectAxis(InputKeys.TOUCH_LOOK, 0.0f, 0.0f);
        assertEquals(List.of("Look:TRIGGERED:-0.50,0.10"), log);
        log.clear();
        input.injectAxis(InputKeys.GAMEPAD_RIGHT_STICK, 0.0f, 0.0f);
        assertEquals(List.of("Look:COMPLETED:0.00,0.00"), log);
    }

    @Test
    public void playerControllerBindsWithoutKeyCodes() {
        PlayerController controller = world.playerController();
        assertNotNull(controller);
        final int[] jumps = {0};
        DelegateHandle handle = controller.bindAction(
                InputAction.JUMP, InputTrigger.STARTED, value -> jumps[0]++);

        GameplayStatics.injectKey(world, InputKeys.SPACE, true);
        assertEquals(1, jumps[0]);
        assertTrue(GameplayStatics.actionValue(world, InputAction.JUMP).isPressed());

        controller.unbind(handle);
        GameplayStatics.injectKey(world, InputKeys.SPACE, false);
        GameplayStatics.injectKey(world, InputKeys.SPACE, true);
        assertEquals(1, jumps[0]);
    }

    @Test
    public void hallGameModeBindsDefaultActions() {
        GameInstance game = GameInstance.withDemoAssets();
        game.init();
        game.openLevel("Hall");
        HallGameMode mode = (HallGameMode) game.gameMode();
        assertNotNull(mode.playerController());

        GameplayStatics.injectKey(game.world(), InputKeys.SPACE, true);
        assertEquals(1, mode.jumpStartedCount());
        GameplayStatics.injectKey(game.world(), InputKeys.SPACE, false);
        assertEquals(1, mode.jumpCompletedCount());

        GameplayStatics.injectAxis(game.world(), InputKeys.TOUCH_MOVE, 0.2f, 0.8f);
        assertEquals(0.2f, mode.lastMove().x(), 0.0001f);
        assertEquals(0.8f, mode.lastMove().y(), 0.0001f);

        game.openLevel("Hall");
        HallGameMode next = (HallGameMode) game.gameMode();
        assertEquals(0, next.jumpStartedCount());
        GameplayStatics.injectKey(game.world(), InputKeys.SPACE, true);
        assertEquals(1, next.jumpStartedCount());
        assertEquals(1, mode.jumpStartedCount());
    }

    @Test
    public void defaultMappingAssetIsRegisteredAndParsesFromJson() {
        AssetRegistry registry = AssetRegistry.withDemoAssets();
        Asset asset = registry.require(AssetRegistry.DEFAULT_MAPPING_ID);
        assertEquals(AssetKind.INPUT_MAPPING, asset.kind());
        assertEquals(AssetRegistry.DEFAULT_MAPPING_PATH, asset.path());
        InputMappingContext fromAsset = asset.as(InputMappingContext.class);
        assertNotNull(fromAsset);
        assertEquals("Default", fromAsset.name());

        InputMappingContext parsed = InputMappingJson.loadDefault();
        assertEquals(fromAsset.mappings().size(), parsed.mappings().size());
        assertTrue(parsed.maps(InputKeys.SPACE, InputAction.JUMP));
        assertTrue(parsed.maps(InputKeys.A, InputAction.MOVE));
    }

    @Test
    public void androidKeyCodesMapToNamedKeys() {
        assertEquals(InputKeys.SPACE, InputKeys.fromKeyCode(62));
        assertEquals(InputKeys.W, InputKeys.fromKeyCode(51));
        assertEquals(InputKeys.A, InputKeys.fromKeyCode(29));
        assertEquals(InputKeys.S, InputKeys.fromKeyCode(47));
        assertEquals(InputKeys.D, InputKeys.fromKeyCode(32));
        assertEquals(InputKeys.ARROW_UP, InputKeys.fromKeyCode(19));
        assertEquals(InputKeys.GAMEPAD_FACE_BOTTOM, InputKeys.fromKeyCode(96));
        assertNull(InputKeys.fromKeyCode(4));
    }

    @Test
    public void playInputRouterFeedsWorldWithoutReplacingStickMachine() {
        bindAll(InputAction.JUMP);
        bindAll(InputAction.MOVE);

        PlayInputRouter.feedKey(world, 62, true);
        PlayInputRouter.feedTouchButton(world, InputKeys.TOUCH_JUMP, false);
        PlayInputRouter.feedTouchAxis(world, InputKeys.TOUCH_MOVE, -0.3f, 0.6f);

        assertTrue(input.actionValue(InputAction.JUMP).isPressed());
        assertEquals(-0.3f, input.actionValue(InputAction.MOVE).x(), 0.0001f);
        assertEquals(0.6f, input.actionValue(InputAction.MOVE).y(), 0.0001f);
        assertTrue(log.contains("Jump:STARTED:1"));
        assertTrue(log.contains("Move:TRIGGERED:-0.30,0.60"));
    }

    @Test
    public void dumpStatAndConsoleListActions() {
        GameInstance game = GameInstance.withDemoAssets();
        game.init();
        game.openLevel("Hall");
        StringBuilder out = new StringBuilder();
        game.world().appendDump(out);
        String dump = out.toString();
        assertTrue(dump.contains("world.actions=3"));
        assertTrue(dump.contains("world.contexts=1"));
        assertTrue(dump.contains("asset id=DefaultMapping"));
        assertTrue(dump.contains("kind=INPUT_MAPPING"));

        String stat = game.console().exec("stat");
        assertTrue(stat.contains("actions=3"));

        String listed = game.console().exec("input");
        assertTrue(listed.contains("actions=3"));
        assertTrue(listed.contains("Jump"));
        assertTrue(listed.contains("Move"));
        assertTrue(listed.contains("Look"));
        assertTrue(listed.contains("Default"));
        assertTrue(game.console().exec("help").contains("input"));
    }

    @Test
    public void worldAndGameInstanceShareInput() {
        GameInstance game = GameInstance.withDemoAssets();
        assertSame(game.input(), game.world().input());
        assertSame(game.playerController(), game.world().playerController());
        assertSame(game.input(), GameplayStatics.getInputSubsystem(game.world()));
        assertSame(game.playerController(), GameplayStatics.getPlayerController(game.world()));
    }

    private void bindAll(String action) {
        input.bindAction(action, InputTrigger.STARTED, value -> log.add(label(action, "STARTED", value)));
        input.bindAction(action, InputTrigger.TRIGGERED, value -> log.add(label(action, "TRIGGERED", value)));
        input.bindAction(action, InputTrigger.COMPLETED, value -> log.add(label(action, "COMPLETED", value)));
    }

    private static String label(String action, String trigger, InputActionValue value) {
        if (value.valueType() == InputValueType.DIGITAL) {
            return action + ":" + trigger + ":" + (value.isPressed() ? "1" : "0");
        }
        return String.format(
                java.util.Locale.US,
                "%s:%s:%.2f,%.2f",
                action,
                trigger,
                value.x(),
                value.y());
    }
}
