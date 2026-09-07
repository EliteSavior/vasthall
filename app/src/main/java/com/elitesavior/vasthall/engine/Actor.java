package com.elitesavior.vasthall.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Gameplay object owned by a {@link World}. Unreal mental model: {@code AActor}.
 *
 * <p>Override {@link #beginPlay()}, {@link #tick(float)}, and {@link #endPlay()}.
 * Do not construct actors with {@code new} and expect them to tick — spawn them
 * through {@link World#spawnActor(Class, Transform)}. Attach behavior with
 * {@link #addComponent(ActorComponent)}.
 */
public class Actor {
    private World world;
    private long id;
    private String name;
    private String levelName;
    private final Transform transform = Transform.identity();
    private final List<ActorComponent> components = new ArrayList<>();
    private boolean tickEnabled = true;
    private boolean pendingKill;

    public long id() {
        return id;
    }

    public String name() {
        if (name == null || name.isEmpty()) {
            return getClass().getSimpleName();
        }
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public World world() {
        return world;
    }

    /** Name of the loaded {@link Level} that spawned this actor, or null. */
    public String levelName() {
        return levelName;
    }

    public Transform transform() {
        return transform;
    }

    public boolean isActorTickEnabled() {
        return tickEnabled;
    }

    public void setActorTickEnabled(boolean enabled) {
        this.tickEnabled = enabled;
    }

    public boolean isPendingKill() {
        return pendingKill;
    }

    /** Destroys this actor if it is still owned by a world. */
    public boolean destroy() {
        World owner = world;
        if (owner == null) {
            return false;
        }
        return owner.destroyActor(this);
    }

    /**
     * Attach {@code component} to this actor and call {@link ActorComponent#onAttach()}.
     * Transform stays on the actor ({@link #transform()}); components add behavior.
     */
    public <T extends ActorComponent> T addComponent(T component) {
        if (component == null) {
            throw new IllegalArgumentException("component");
        }
        if (component.owner() != null) {
            throw new IllegalStateException("component already attached");
        }
        components.add(component);
        component.attachTo(this);
        return component;
    }

    public boolean removeComponent(ActorComponent component) {
        if (component == null || component.owner() != this) {
            return false;
        }
        components.remove(component);
        component.detachFromOwner();
        return true;
    }

    /** First attached component of {@code type}, or null. */
    public <T extends ActorComponent> T getComponent(Class<T> type) {
        if (type == null) {
            return null;
        }
        for (ActorComponent component : components) {
            if (type.isInstance(component)) {
                return type.cast(component);
            }
        }
        return null;
    }

    public <T extends ActorComponent> List<T> componentsOf(Class<T> type) {
        List<T> matched = new ArrayList<>();
        if (type == null) {
            return matched;
        }
        for (ActorComponent component : components) {
            if (type.isInstance(component)) {
                matched.add(type.cast(component));
            }
        }
        return matched;
    }

    /** Mutable copy. Clearing the list does not change the actor. */
    public List<ActorComponent> components() {
        return new ArrayList<>(components);
    }

    public int componentCount() {
        return components.size();
    }

    void attach(World world, long id, Transform spawnTransform) {
        this.world = world;
        this.id = id;
        this.pendingKill = false;
        this.levelName = null;
        if (spawnTransform != null) {
            this.transform.copyFrom(spawnTransform);
        }
        if (this.name == null) {
            this.name = getClass().getSimpleName();
        }
    }

    void bindLevel(String levelName) {
        this.levelName = levelName;
    }

    void markPendingKill() {
        pendingKill = true;
    }

    void detach() {
        world = null;
        pendingKill = true;
        levelName = null;
    }

    void callBeginPlay() {
        beginPlay();
    }

    void callEndPlay() {
        try {
            endPlay();
        } finally {
            destroyComponents();
        }
    }

    void callTick(float deltaSeconds) {
        List<ActorComponent> snapshot = new ArrayList<>(components);
        tick(deltaSeconds);
        for (ActorComponent component : snapshot) {
            if (component.owner() == this && component.isComponentTickEnabled()) {
                component.callTick(deltaSeconds);
            }
        }
    }

    void appendComponentDump(StringBuilder out) {
        for (ActorComponent component : components) {
            component.appendDump(out);
        }
    }

    private void destroyComponents() {
        List<ActorComponent> snapshot = new ArrayList<>(components);
        components.clear();
        for (ActorComponent component : snapshot) {
            if (component.owner() == this) {
                component.detachFromOwner();
            }
        }
    }

    /** Called once after spawn, before the first {@link #tick(float)}. */
    protected void beginPlay() {
    }

    /** Called each world tick while {@link #isActorTickEnabled()} is true. */
    protected void tick(float deltaSeconds) {
    }

    /** Called once when the world destroys this actor. */
    protected void endPlay() {
    }
}
