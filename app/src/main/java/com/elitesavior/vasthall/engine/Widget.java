package com.elitesavior.vasthall.engine;

/**
 * UMG-lite user widget. Unreal mental model: {@code UUserWidget} —
 * {@code CreateWidget} then {@code AddToViewport} / {@code RemoveFromParent}.
 *
 * <p>This is a gameplay object, not an Android View. A {@link WidgetHost}
 * may mirror it onto the play overlay. Visibility is independent of the
 * viewport slot: hide keeps the widget added; remove drops the slot.
 */
public class Widget {
    private String name = "";
    private WidgetViewport viewport;
    private boolean inViewport;
    private WidgetVisibility visibility = WidgetVisibility.VISIBLE;

    public String name() {
        return name;
    }

    public WidgetViewport viewport() {
        return viewport;
    }

    public boolean isInViewport() {
        return inViewport;
    }

    public WidgetVisibility visibility() {
        return visibility;
    }

    public boolean isVisible() {
        return inViewport && visibility == WidgetVisibility.VISIBLE;
    }

    /** {@code AddToViewport}. False if this widget was never created on a viewport. */
    public boolean addToViewport() {
        if (viewport == null) {
            return false;
        }
        return viewport.addToViewport(this);
    }

    /** {@code RemoveFromParent}. False if the widget is not on the viewport. */
    public boolean removeFromParent() {
        if (viewport == null) {
            return false;
        }
        return viewport.removeFromParent(this);
    }

    public void setVisibility(WidgetVisibility visibility) {
        WidgetVisibility next = visibility == null ? WidgetVisibility.VISIBLE : visibility;
        if (this.visibility == next) {
            return;
        }
        this.visibility = next;
        if (viewport != null) {
            viewport.notifyChanged(this);
        }
    }

    public void show() {
        setVisibility(WidgetVisibility.VISIBLE);
    }

    public void hide() {
        setVisibility(WidgetVisibility.HIDDEN);
    }

    void bind(WidgetViewport viewport, String name) {
        this.viewport = viewport;
        this.name = name == null ? "" : name;
    }

    void markInViewport(boolean inViewport) {
        this.inViewport = inViewport;
    }

    void unbind() {
        inViewport = false;
        viewport = null;
    }
}
