package com.elitesavior.vasthall.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Short names for actor classes used in level JSON / templates.
 * Unreal analog: the class path on a placed actor.
 */
public final class ActorTypes {
    private static final String ENGINE_PACKAGE = "com.elitesavior.vasthall.engine";
    private static final Map<String, Class<? extends Actor>> TYPES = new LinkedHashMap<>();

    static {
        register("Actor", Actor.class);
        register("PlayerPawn", PlayerPawn.class);
        register("HallBeaconActor", HallBeaconActor.class);
    }

    private ActorTypes() {
    }

    public static synchronized void register(String name, Class<? extends Actor> type) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("actor type name");
        }
        if (type == null) {
            throw new IllegalArgumentException("actor type");
        }
        TYPES.put(name, type);
    }

    public static synchronized Class<? extends Actor> resolve(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("unknown actor class: " + name);
        }
        Class<? extends Actor> registered = TYPES.get(name);
        if (registered != null) {
            return registered;
        }
        String fqcn = name.indexOf('.') >= 0 ? name : ENGINE_PACKAGE + '.' + name;
        try {
            Class<?> loaded = Class.forName(fqcn);
            if (Actor.class.isAssignableFrom(loaded)) {
                @SuppressWarnings("unchecked")
                Class<? extends Actor> actorType = (Class<? extends Actor>) loaded;
                TYPES.put(name, actorType);
                return actorType;
            }
        } catch (ClassNotFoundException ignored) {
        }
        throw new IllegalArgumentException("unknown actor class: " + name);
    }
}
