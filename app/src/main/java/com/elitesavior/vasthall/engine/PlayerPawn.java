package com.elitesavior.vasthall.engine;

/**
 * Java-side stand-in for the native player avatar. Locomotion still lives in
 * {@code libvasthall.so}; this actor is the World-owned handle gameplay code
 * can find, query, and attach components to. Locomotion is not a Java
 * {@link MovementComponent} — native still walks. This pawn carries a
 * {@link TagComponent} {@code pawn} and a {@link CollisionComponent} box
 * so the Scene dump shows overlap primitives.
 */
public class PlayerPawn extends Actor {
    public static final String DEFAULT_NAME = "PlayerPawn";

    public PlayerPawn() {
        setName(DEFAULT_NAME);
        setActorTickEnabled(false);
        addComponent(new TagComponent("pawn"));
        addComponent(new CollisionComponent().setBoxExtent(0.4f, 0.9f, 0.4f));
        gameplayTags().addTag(GameplayTag.CHARACTER_PLAYER);
    }
}
