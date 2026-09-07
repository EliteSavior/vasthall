package com.elitesavior.vasthall.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Short names for {@link GameMode} classes used in level JSON / templates.
 * Unreal analog: the GameMode class path on a map.
 */
public final class GameModeTypes {
    private static final String ENGINE_PACKAGE = "com.elitesavior.vasthall.engine";
    private static final Map<String, Class<? extends GameMode>> TYPES = new LinkedHashMap<>();

    static {
        register("GameMode", GameMode.class);
        register("HallGameMode", HallGameMode.class);
    }

    private GameModeTypes() {
    }

    public static synchronized void register(String name, Class<? extends GameMode> type) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("game mode type name");
        }
        if (type == null) {
            throw new IllegalArgumentException("game mode type");
        }
        TYPES.put(name, type);
    }

    public static synchronized Class<? extends GameMode> resolve(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("unknown game mode class: " + name);
        }
        Class<? extends GameMode> registered = TYPES.get(name);
        if (registered != null) {
            return registered;
        }
        String fqcn = name.indexOf('.') >= 0 ? name : ENGINE_PACKAGE + '.' + name;
        try {
            Class<?> loaded = Class.forName(fqcn);
            if (GameMode.class.isAssignableFrom(loaded)) {
                @SuppressWarnings("unchecked")
                Class<? extends GameMode> modeType = (Class<? extends GameMode>) loaded;
                TYPES.put(name, modeType);
                return modeType;
            }
        } catch (ClassNotFoundException ignored) {
        }
        throw new IllegalArgumentException("unknown game mode class: " + name);
    }
}
