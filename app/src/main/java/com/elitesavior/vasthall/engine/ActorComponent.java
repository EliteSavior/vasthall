package com.elitesavior.vasthall.engine;

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
    private boolean tickEnabled = true;
    private boolean attached;

    public Actor owner() {
        return owner;
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
