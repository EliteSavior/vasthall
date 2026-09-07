package com.elitesavior.vasthall;

import com.elitesavior.vasthall.engine.InputKeys;
import com.elitesavior.vasthall.engine.InputSubsystem;
import com.elitesavior.vasthall.engine.World;

/**
 * Feeds the Enhanced Input–lite {@link InputSubsystem} from Activity
 * key / touch / stick callbacks. Does not replace {@link PlayInputMachine}
 * or the native JNI axis path.
 */
public final class PlayInputRouter {
    private PlayInputRouter() {
    }

    public static void feedKey(World world, int keyCode, boolean down) {
        if (world == null) {
            return;
        }
        String key = InputKeys.fromKeyCode(keyCode);
        if (key != null) {
            world.input().injectKey(key, down);
        }
    }

    public static void feedTouchButton(World world, String key, boolean down) {
        if (world == null || key == null) {
            return;
        }
        world.input().injectKey(key, down);
    }

    public static void feedTouchAxis(World world, String key, float x, float y) {
        if (world == null || key == null) {
            return;
        }
        world.input().injectAxis(key, x, y);
    }
}
