package com.elitesavior.vasthall.engine;

/**
 * Gameplay object owned by a {@link World}. Unreal mental model: {@code AActor}.
 *
 * <p>Override {@link #beginPlay()}, {@link #tick(float)}, and {@link #endPlay()}.
 * Do not construct actors with {@code new} and expect them to tick — spawn them
 * through {@link World#spawnActor(Class, Transform)}.
 */
public class Actor {
    private World world;
    private long id;
    private String name;
    private final Transform transform = Transform.identity();
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

    void attach(World world, long id, Transform spawnTransform) {
        this.world = world;
        this.id = id;
        this.pendingKill = false;
        if (spawnTransform != null) {
            this.transform.copyFrom(spawnTransform);
        }
        if (this.name == null) {
            this.name = getClass().getSimpleName();
        }
    }

    void markPendingKill() {
        pendingKill = true;
    }

    void detach() {
        world = null;
        pendingKill = true;
    }

    void callBeginPlay() {
        beginPlay();
    }

    void callEndPlay() {
        endPlay();
    }

    void callTick(float deltaSeconds) {
        tick(deltaSeconds);
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
