package com.elitesavior.vasthall.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * World-owned Enhanced Input–lite router. Named {@link InputAction}s are
 * bound by GameMode / {@link PlayerController}; the activity injects keys
 * and axes. This does not own stick lockup or the native JNI path.
 */
public final class InputSubsystem {
    private static final class Binding {
        final String action;
        final InputTrigger trigger;
        final Consumer<InputActionValue> listener;
        final DelegateHandle handle;

        Binding(String action, InputTrigger trigger, Consumer<InputActionValue> listener, DelegateHandle handle) {
            this.action = action;
            this.trigger = trigger;
            this.listener = listener;
            this.handle = handle;
        }
    }

    private static final class Contribution {
        boolean digital;
        float x;
        float y;
    }

    private static final class ActionState {
        final InputAction action;
        final Map<String, Contribution> contributions = new LinkedHashMap<>();
        boolean active;
        float x;
        float y;
        boolean pressed;

        ActionState(InputAction action) {
            this.action = action;
        }

        InputActionValue value() {
            if (action.valueType() == InputValueType.DIGITAL) {
                return InputActionValue.digital(pressed);
            }
            if (action.valueType() == InputValueType.AXIS1D) {
                return InputActionValue.axis1D(x);
            }
            return InputActionValue.axis2D(x, y);
        }

        void recompute() {
            boolean anyDigital = false;
            float sumX = 0.0f;
            float sumY = 0.0f;
            for (Contribution contribution : contributions.values()) {
                anyDigital |= contribution.digital;
                sumX += contribution.x;
                sumY += contribution.y;
            }
            pressed = anyDigital || sumX != 0.0f || sumY != 0.0f;
            x = sumX;
            y = sumY;
            if (action.valueType() == InputValueType.DIGITAL) {
                pressed = anyDigital || !contributions.isEmpty();
                x = pressed ? 1.0f : 0.0f;
                y = 0.0f;
            }
        }
    }

    private final World world;
    private final List<InputMappingContext> contexts = new ArrayList<>();
    private final Map<String, ActionState> states = new LinkedHashMap<>();
    private final Map<DelegateHandle, Binding> bindings = new LinkedHashMap<>();
    private long nextBindId = 1L;

    public InputSubsystem(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world");
        }
        this.world = world;
    }

    public World world() {
        return world;
    }

    public void addMappingContext(InputMappingContext context) {
        if (context == null) {
            throw new IllegalArgumentException("mapping context");
        }
        if (contexts.contains(context)) {
            return;
        }
        contexts.add(context);
        for (InputAction action : context.actions()) {
            states.computeIfAbsent(action.name(), ignored -> new ActionState(action));
        }
    }

    public void removeMappingContext(InputMappingContext context) {
        contexts.remove(context);
    }

    public List<InputMappingContext> mappingContexts() {
        return new ArrayList<>(contexts);
    }

    public InputAction action(String name) {
        ActionState state = states.get(name);
        if (state != null) {
            return state.action;
        }
        for (InputMappingContext context : contexts) {
            InputAction found = context.action(name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public int actionCount() {
        return states.size();
    }

    public int contextCount() {
        return contexts.size();
    }

    public DelegateHandle bindAction(
            String action, InputTrigger trigger, Consumer<InputActionValue> listener) {
        if (action == null || action.trim().isEmpty()) {
            throw new IllegalArgumentException("action");
        }
        if (trigger == null) {
            throw new IllegalArgumentException("trigger");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener");
        }
        DelegateHandle handle = new DelegateHandle(nextBindId++);
        bindings.put(handle, new Binding(action.trim(), trigger, listener, handle));
        return handle;
    }

    public void unbind(DelegateHandle handle) {
        if (handle == null || !handle.isValid()) {
            return;
        }
        Binding removed = bindings.remove(handle);
        if (removed != null) {
            handle.invalidate();
        }
    }

    public InputActionValue actionValue(String action) {
        ActionState state = states.get(action);
        if (state == null) {
            InputAction declared = action(action);
            return InputActionValue.zero(declared == null ? InputValueType.DIGITAL : declared.valueType());
        }
        return state.value();
    }

    public boolean isPressed(String action) {
        return actionValue(action).isPressed();
    }

    /** Digital key / touch button / gamepad face button. */
    public void injectKey(String key, boolean down) {
        if (key == null || key.isEmpty()) {
            return;
        }
        for (InputMapping mapping : mappingsFor(key)) {
            ActionState state = stateFor(mapping);
            if (state.action.valueType() == InputValueType.DIGITAL) {
                applyDigital(state, key, down);
            } else {
                float x = 0.0f;
                float y = 0.0f;
                if (down) {
                    if (mapping.axis() == InputMapping.Axis.X) {
                        x = mapping.scale();
                    } else if (mapping.axis() == InputMapping.Axis.Y) {
                        y = mapping.scale();
                    } else {
                        x = mapping.scale();
                    }
                }
                applyAxis(state, key, x, y, down);
            }
        }
    }

    /** Analog stick / 2D axis source (touch move/look, gamepad stick stub). */
    public void injectAxis(String key, float x, float y) {
        if (key == null || key.isEmpty()) {
            return;
        }
        if (Float.isNaN(x) || Float.isNaN(y) || Float.isInfinite(x) || Float.isInfinite(y)) {
            throw new IllegalArgumentException("axis");
        }
        for (InputMapping mapping : mappingsFor(key)) {
            ActionState state = stateFor(mapping);
            if (state.action.valueType() == InputValueType.DIGITAL) {
                applyDigital(state, key, x != 0.0f || y != 0.0f);
                continue;
            }
            float mappedX = x;
            float mappedY = y;
            if (mapping.axis() == InputMapping.Axis.X) {
                mappedX = x * mapping.scale();
                mappedY = 0.0f;
            } else if (mapping.axis() == InputMapping.Axis.Y) {
                mappedX = 0.0f;
                mappedY = y * mapping.scale();
            } else if (mapping.scale() != 1.0f) {
                mappedX = x * mapping.scale();
                mappedY = y * mapping.scale();
            }
            applyAxis(state, key, mappedX, mappedY, mappedX != 0.0f || mappedY != 0.0f);
        }
    }

    void appendDump(StringBuilder out) {
        out.append("world.actions=").append(actionCount()).append('\n');
        out.append("world.contexts=").append(contextCount()).append('\n');
    }

    List<String> describe() {
        List<String> lines = new ArrayList<>();
        lines.add("contexts=" + contextCount());
        for (InputMappingContext context : contexts) {
            lines.add("context=" + context.name() + " mappings=" + context.mappings().size());
        }
        for (ActionState state : states.values()) {
            InputActionValue value = state.value();
            if (state.action.valueType() == InputValueType.DIGITAL) {
                lines.add(String.format(
                        Locale.US,
                        "action=%s type=DIGITAL pressed=%d",
                        state.action.name(),
                        value.isPressed() ? 1 : 0));
            } else {
                lines.add(String.format(
                        Locale.US,
                        "action=%s type=%s x=%.2f y=%.2f",
                        state.action.name(),
                        state.action.valueType().name(),
                        value.x(),
                        value.y()));
            }
        }
        return lines;
    }

    private List<InputMapping> mappingsFor(String key) {
        List<InputMapping> matched = new ArrayList<>();
        for (InputMappingContext context : contexts) {
            matched.addAll(context.mappingsFor(key));
        }
        return matched;
    }

    private ActionState stateFor(InputMapping mapping) {
        ActionState existing = states.get(mapping.action());
        if (existing != null) {
            return existing;
        }
        InputAction declared = action(mapping.action());
        InputAction created = declared != null
                ? declared
                : new InputAction(mapping.action(), inferType(mapping));
        ActionState state = new ActionState(created);
        states.put(mapping.action(), state);
        return state;
    }

    private static InputValueType inferType(InputMapping mapping) {
        if (mapping.axis() != InputMapping.Axis.NONE) {
            return InputValueType.AXIS2D;
        }
        return InputValueType.DIGITAL;
    }

    private void applyDigital(ActionState state, String key, boolean down) {
        InputActionValue before = state.value();
        boolean wasActive = state.active;
        if (down) {
            Contribution contribution = state.contributions.computeIfAbsent(key, ignored -> new Contribution());
            contribution.digital = true;
            contribution.x = 1.0f;
            contribution.y = 0.0f;
        } else {
            state.contributions.remove(key);
        }
        state.recompute();
        dispatch(state, before, wasActive);
    }

    private void applyAxis(ActionState state, String key, float x, float y, boolean present) {
        InputActionValue before = state.value();
        boolean wasActive = state.active;
        if (present) {
            Contribution contribution = state.contributions.computeIfAbsent(key, ignored -> new Contribution());
            contribution.digital = false;
            contribution.x = x;
            contribution.y = y;
        } else {
            state.contributions.remove(key);
        }
        state.recompute();
        dispatch(state, before, wasActive);
    }

    private void dispatch(ActionState state, InputActionValue before, boolean wasActive) {
        InputActionValue now = state.value();
        boolean nowActive = state.action.valueType() == InputValueType.DIGITAL
                ? now.isPressed()
                : now.x() != 0.0f || now.y() != 0.0f;
        if (wasActive == nowActive
                && before.isPressed() == now.isPressed()
                && before.x() == now.x()
                && before.y() == now.y()) {
            state.active = nowActive;
            return;
        }
        if (!wasActive && nowActive) {
            fire(state.action.name(), InputTrigger.STARTED, now);
            fire(state.action.name(), InputTrigger.TRIGGERED, now);
        } else if (wasActive && nowActive) {
            fire(state.action.name(), InputTrigger.TRIGGERED, now);
        } else if (wasActive) {
            fire(state.action.name(), InputTrigger.COMPLETED, now);
        }
        state.active = nowActive;
    }

    private void fire(String action, InputTrigger trigger, InputActionValue value) {
        List<Binding> snapshot = new ArrayList<>(bindings.values());
        for (Binding binding : snapshot) {
            if (binding.action.equals(action) && binding.trigger == trigger) {
                binding.listener.accept(value);
            }
        }
    }
}
