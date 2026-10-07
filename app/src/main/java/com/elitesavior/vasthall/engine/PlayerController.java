package com.elitesavior.vasthall.engine;

import java.util.function.Consumer;

/**
 * Local-player bind surface. Unreal analog: a thin {@code APlayerController}
 * — GameMode binds named actions here instead of Activity key codes.
 */
public final class PlayerController {
    private final InputSubsystem input;

    public PlayerController(InputSubsystem input) {
        if (input == null) {
            throw new IllegalArgumentException("input");
        }
        this.input = input;
    }

    public InputSubsystem input() {
        return input;
    }

    public DelegateHandle bindAction(
            String action, InputTrigger trigger, Consumer<InputActionValue> listener) {
        return input.bindAction(action, trigger, listener);
    }

    public DelegateHandle bindAxis(String action, Consumer<InputActionValue> listener) {
        return input.bindAction(action, InputTrigger.TRIGGERED, listener);
    }

    public void unbind(DelegateHandle handle) {
        input.unbind(handle);
    }

    public InputActionValue actionValue(String action) {
        return input.actionValue(action);
    }
}
