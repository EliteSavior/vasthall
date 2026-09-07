package com.elitesavior.vasthall.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Named set of key → action mappings. Unreal analog:
 * {@code UInputMappingContext}. The default asset lives at
 * {@code input/DefaultMapping.json}.
 */
public final class InputMappingContext {
    private final String name;
    private final Map<String, InputAction> actions = new LinkedHashMap<>();
    private final List<InputMapping> mappings = new ArrayList<>();

    public InputMappingContext(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("mapping context name");
        }
        this.name = name.trim();
    }

    /** Loads {@code input/DefaultMapping.json}. */
    public static InputMappingContext defaults() {
        return InputMappingJson.loadDefault();
    }

    public String name() {
        return name;
    }

    public InputMappingContext addAction(InputAction action) {
        if (action == null) {
            throw new IllegalArgumentException("action");
        }
        actions.put(action.name(), action);
        return this;
    }

    public InputMappingContext map(String action, String key) {
        return map(new InputMapping(action, key));
    }

    public InputMappingContext map(String action, String key, InputMapping.Axis axis, float scale) {
        return map(new InputMapping(action, key, axis, scale));
    }

    public InputMappingContext map(InputMapping mapping) {
        if (mapping == null) {
            throw new IllegalArgumentException("mapping");
        }
        mappings.add(mapping);
        return this;
    }

    public InputAction action(String name) {
        if (name == null) {
            return null;
        }
        return actions.get(name);
    }

    public List<InputAction> actions() {
        return new ArrayList<>(actions.values());
    }

    public List<InputMapping> mappings() {
        return new ArrayList<>(mappings);
    }

    public List<InputMapping> mappingsFor(String key) {
        List<InputMapping> matched = new ArrayList<>();
        if (key == null) {
            return matched;
        }
        for (InputMapping mapping : mappings) {
            if (mapping.key().equals(key)) {
                matched.add(mapping);
            }
        }
        return matched;
    }

    public boolean maps(String key, String action) {
        if (key == null || action == null) {
            return false;
        }
        for (InputMapping mapping : mappings) {
            if (mapping.key().equals(key) && mapping.action().equals(action)) {
                return true;
            }
        }
        return false;
    }
}
