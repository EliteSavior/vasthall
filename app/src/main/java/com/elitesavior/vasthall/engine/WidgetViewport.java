package com.elitesavior.vasthall.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Root viewport / HUD host. Unreal mental model: {@code AddToViewport}
 * on {@code UUserWidget} — a named slot list the play overlay can
 * mirror without rewriting the Android stick stack.
 *
 * <p>Create does not show. {@link #addToViewport} shows. {@link Widget#hide()}
 * keeps the slot. {@link #removeFromParent} drops the slot. Destroy
 * forgets the name so GameMode can recreate the sample on travel.
 */
public final class WidgetViewport {
    private final Map<String, Widget> created = new LinkedHashMap<>();
    private final List<Widget> slotted = new ArrayList<>();
    private WidgetHost host = new SilentWidgetHost();

    public WidgetHost host() {
        return host;
    }

    /** Tests and the activity inject a recording or Android overlay host. */
    public void setHost(WidgetHost host) {
        this.host = host == null ? new SilentWidgetHost() : host;
    }

    public <T extends Widget> T createWidget(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("widget type");
        }
        String base = type.getSimpleName();
        String name = base;
        int suffix = 1;
        while (created.containsKey(name)) {
            name = base + suffix++;
        }
        return createWidget(type, name);
    }

    public <T extends Widget> T createWidget(Class<T> type, String name) {
        if (type == null) {
            throw new IllegalArgumentException("widget type");
        }
        String key = requireName(name);
        if (created.containsKey(key)) {
            throw new IllegalArgumentException("widget already exists: " + key);
        }
        T widget;
        try {
            widget = type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException failed) {
            throw new IllegalArgumentException(
                    "Widget type needs a public no-arg constructor: " + type.getName(),
                    failed);
        }
        widget.bind(this, key);
        created.put(key, widget);
        return widget;
    }

    public Widget createWidget(String typeName, String name) {
        return createWidget(WidgetTypes.resolve(typeName), name);
    }

    public boolean addToViewport(Widget widget) {
        Widget live = requireOwned(widget);
        if (live == null) {
            return false;
        }
        if (live.isInViewport()) {
            return true;
        }
        live.markInViewport(true);
        slotted.add(live);
        host.widgetAdded(live);
        return true;
    }

    public boolean removeFromParent(Widget widget) {
        Widget live = requireOwned(widget);
        if (live == null || !live.isInViewport()) {
            return false;
        }
        live.markInViewport(false);
        slotted.remove(live);
        host.widgetRemoved(live);
        return true;
    }

    public boolean destroyWidget(Widget widget) {
        Widget live = requireOwned(widget);
        if (live == null) {
            return false;
        }
        removeFromParent(live);
        created.remove(live.name());
        live.unbind();
        return true;
    }

    public void removeAll() {
        List<Widget> snapshot = new ArrayList<>(created.values());
        for (Widget widget : snapshot) {
            destroyWidget(widget);
        }
    }

    public Widget find(String name) {
        if (name == null) {
            return null;
        }
        return created.get(name);
    }

    public boolean show(String name) {
        Widget widget = find(name);
        if (widget == null) {
            return false;
        }
        widget.show();
        return true;
    }

    public boolean hide(String name) {
        Widget widget = find(name);
        if (widget == null) {
            return false;
        }
        widget.hide();
        return true;
    }

    public int widgetCount() {
        return created.size();
    }

    public int viewportCount() {
        return slotted.size();
    }

    public int visibleCount() {
        int count = 0;
        for (Widget widget : slotted) {
            if (widget.isVisible()) {
                count++;
            }
        }
        return count;
    }

    public List<Widget> widgets() {
        return new ArrayList<>(created.values());
    }

    public List<Widget> viewportWidgets() {
        return new ArrayList<>(slotted);
    }

    void notifyChanged(Widget widget) {
        if (requireOwned(widget) == null) {
            return;
        }
        host.widgetChanged(widget);
    }

    void appendDump(StringBuilder out) {
        out.append("world.widgets=").append(slotted.size()).append('\n');
    }

    List<String> describe() {
        List<String> lines = new ArrayList<>(created.size());
        for (Widget widget : created.values()) {
            StringBuilder line = new StringBuilder();
            line.append(widget.name())
                    .append(" class=").append(widget.getClass().getSimpleName())
                    .append(" viewport=").append(widget.isInViewport() ? 1 : 0)
                    .append(" vis=").append(widget.visibility().name());
            if (widget instanceof TextWidget) {
                line.append(" text=").append(((TextWidget) widget).text());
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private Widget requireOwned(Widget widget) {
        if (widget == null || widget.viewport() != this) {
            return null;
        }
        return widget;
    }

    private static String requireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("widget name");
        }
        return name.trim();
    }
}
