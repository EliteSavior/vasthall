package com.elitesavior.vasthall.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Short names for {@link Widget} classes used by the console /
 * {@link WidgetViewport#createWidget(String, String)}.
 * Unreal analog: the Widget Blueprint class path on {@code CreateWidget}.
 */
public final class WidgetTypes {
    private static final String ENGINE_PACKAGE = "com.elitesavior.vasthall.engine";
    private static final Map<String, Class<? extends Widget>> TYPES = new LinkedHashMap<>();

    static {
        register("Widget", Widget.class);
        register("Text", TextWidget.class);
        register("TextWidget", TextWidget.class);
    }

    private WidgetTypes() {
    }

    public static synchronized void register(String name, Class<? extends Widget> type) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("widget type name");
        }
        if (type == null) {
            throw new IllegalArgumentException("widget type");
        }
        TYPES.put(name, type);
    }

    public static synchronized Class<? extends Widget> resolve(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("unknown widget class: " + name);
        }
        Class<? extends Widget> registered = TYPES.get(name);
        if (registered != null) {
            return registered;
        }
        String fqcn = name.indexOf('.') >= 0 ? name : ENGINE_PACKAGE + '.' + name;
        try {
            Class<?> loaded = Class.forName(fqcn);
            if (Widget.class.isAssignableFrom(loaded)) {
                @SuppressWarnings("unchecked")
                Class<? extends Widget> widgetType = (Class<? extends Widget>) loaded;
                TYPES.put(name, widgetType);
                return widgetType;
            }
        } catch (ClassNotFoundException ignored) {
        }
        throw new IllegalArgumentException("unknown widget class: " + name);
    }
}
