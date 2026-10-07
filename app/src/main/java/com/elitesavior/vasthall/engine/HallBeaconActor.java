package com.elitesavior.vasthall.engine;

/**
 * Demo actor placed by the Hall sample level so the Scene/Actor layer is visible.
 * Bobs on Y and yaws every tick. Native hall mesh is still owned by
 * {@code libvasthall.so}; this is the Java-side engine object. Ships a
 * {@link TagComponent} {@code beacon} and a {@link CollisionComponent} box
 * so the dump shows overlap primitives without changing native locomotion.
 * Resolves {@code /Game/Textures/HallBeacon} from the world
 * {@link AssetRegistry} in {@code beginPlay}.
 */
public class HallBeaconActor extends Actor {
    public static final String DEFAULT_NAME = "HallBeacon";
    public static final float BASE_Y = 1.5f;
    public static final float BOB_AMPLITUDE = 0.25f;
    public static final float YAW_DEGREES_PER_SECOND = 45.0f;

    private float age;
    private TextureHandle texture;

    public HallBeaconActor() {
        setName(DEFAULT_NAME);
        setActorTickEnabled(true);
        addComponent(new TagComponent("beacon"));
        addComponent(new CollisionComponent().setBoxExtent(0.3f, 0.3f, 0.3f));
        gameplayTags().addTag(GameplayTag.WORLD_LANDMARK_BEACON);
    }

    /** Texture resolved from the world registry in {@link #beginPlay()}. */
    public TextureHandle texture() {
        return texture;
    }

    @Override
    protected void beginPlay() {
        texture = loadAsset(AssetRegistry.HALL_BEACON_TEXTURE_ID, TextureHandle.class);
    }

    @Override
    protected void tick(float deltaSeconds) {
        age += deltaSeconds;
        Transform t = transform();
        t.location.y = BASE_Y + (float) Math.sin(age) * BOB_AMPLITUDE;
        t.rotation.yaw = wrapDegrees(t.rotation.yaw + YAW_DEGREES_PER_SECOND * deltaSeconds);
    }

    static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped < 0.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }
}
