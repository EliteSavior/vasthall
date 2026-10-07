package com.elitesavior.vasthall.engine;

import java.util.ArrayList;

/**
 * Piece of actor behavior owned by an {@link Actor}. Unreal mental model:
 * {@code UActorComponent}.
 *
 * <p>Override {@link #onAttach()}, {@link #onDetach()}, and {@link #tick(float)}.
 * Attach through {@link Actor#addComponent(ActorComponent)} — do not call
 * {@link #onAttach()} yourself.
 */
public class ActorComponent {
    private Actor owner;
    private final ArrayList<TimerHandle> ownedTimers = new ArrayList<>();
    private boolean tickEnabled = true;
    private boolean attached;

    public Actor owner() {
        return owner;
    }

    public TimerManager timerManager() {
        return owner == null ? null : owner.timerManager();
    }

    /**
     * Schedule a callback on the owner actor's world {@link TimerManager}.
     * Cleared automatically in {@link #onDetach()}.
     */
    public TimerHandle setTimer(Runnable callback, float rateSeconds, boolean looping) {
        TimerManager manager = timerManager();
        if (manager == null) {
            throw new IllegalStateException("no world");
        }
        TimerHandle handle = manager.setTimer(callback, rateSeconds, looping);
        ownedTimers.add(handle);
        return handle;
    }

    public void clearTimer(TimerHandle handle) {
        TimerManager manager = timerManager();
        if (manager != null) {
            manager.clearTimer(handle);
        } else if (handle != null) {
            handle.invalidate();
        }
        ownedTimers.remove(handle);
    }

    public boolean isAttached() {
        return attached;
    }

    public boolean isComponentTickEnabled() {
        return tickEnabled;
    }

    public void setComponentTickEnabled(boolean enabled) {
        this.tickEnabled = enabled;
    }

    void attachTo(Actor owner) {
        this.owner = owner;
        this.attached = true;
        onAttach();
    }

    void detachFromOwner() {
        try {
            onDetach();
        } finally {
            clearOwnedTimers();
            this.owner = null;
            this.attached = false;
        }
    }

    void callTick(float deltaSeconds) {
        tick(deltaSeconds);
    }

    void appendDump(StringBuilder out) {
        out.append("  component class=").append(getClass().getSimpleName())
                .append(" tick=").append(tickEnabled ? 1 : 0);
        appendDumpFields(out);
        out.append('\n');
    }

    /** Extra dump fields; subclasses append {@code " key=value"} fragments. */
    protected void appendDumpFields(StringBuilder out) {
    }

    private void clearOwnedTimers() {
        TimerManager manager = timerManager();
        for (TimerHandle handle : ownedTimers) {
            if (manager != null) {
                manager.clearTimer(handle);
            } else if (handle != null) {
                handle.invalidate();
            }
        }
        ownedTimers.clear();
    }

    /** Called once when the component is added to an actor. */
    protected void onAttach() {
    }

    /** Called once when the component is removed, or the owner actor is destroyed. */
    protected void onDetach() {
    }

    /**
     * Called each world tick while the owner actor ticks and
     * {@link #isComponentTickEnabled()} is true.
     */
    protected void tick(float deltaSeconds) {
    }
}
