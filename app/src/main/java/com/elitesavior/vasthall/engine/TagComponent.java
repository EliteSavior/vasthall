package com.elitesavior.vasthall.engine;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Named tags on an actor (query, filter, dump). Tick is off by default —
 * tags are data, not motion. Unreal analog: a tiny {@code ActorTags} /
 * gameplay-tag component rather than a second name field ({@link Actor}
 * already has {@link Actor#name()}).
 */
public class TagComponent extends ActorComponent {
    private final LinkedHashSet<String> tags = new LinkedHashSet<>();

    public TagComponent() {
        setComponentTickEnabled(false);
    }

    public TagComponent(String... initial) {
        this();
        if (initial == null) {
            return;
        }
        for (String tag : initial) {
            addTag(tag);
        }
    }

    public boolean addTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            throw new IllegalArgumentException("tag");
        }
        return tags.add(tag);
    }

    public boolean removeTag(String tag) {
        return tags.remove(tag);
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    /** Copy; mutating the set does not change the component. */
    public Set<String> tags() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(tags));
    }

    @Override
    protected void appendDumpFields(StringBuilder out) {
        out.append(" tags=");
        boolean first = true;
        for (String tag : tags) {
            if (!first) {
                out.append(',');
            }
            out.append(tag);
            first = false;
        }
    }
}
