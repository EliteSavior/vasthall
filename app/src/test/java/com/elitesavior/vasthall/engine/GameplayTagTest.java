package com.elitesavior.vasthall.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;

public final class GameplayTagTest {
    @Test
    public void ofParsesDottedHierarchyAndRejectsBlank() {
        GameplayTag burning = GameplayTag.of("Character.Status.Burning");
        assertEquals("Character.Status.Burning", burning.name());
        assertEquals("Character.Status", burning.parent().name());
        assertEquals("Character", burning.parent().parent().name());
        assertNull(burning.parent().parent().parent());
        assertEquals(3, burning.depth());
        assertTrue(burning.isChildOf(GameplayTag.of("Character.Status")));
        assertTrue(burning.isChildOf(GameplayTag.of("Character")));
        assertFalse(burning.isChildOf(burning));
        assertTrue(GameplayTag.of("Character").isParentOf(burning));
        assertEquals(burning, GameplayTag.of("Character.Status.Burning"));
        assertEquals(burning.hashCode(), GameplayTag.of("Character.Status.Burning").hashCode());
        assertNotEquals(burning, GameplayTag.of("Character.Status"));
        try {
            GameplayTag.of("  ");
            fail("expected blank tag");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("tag"));
        }
        try {
            GameplayTag.of("Character..Burning");
            fail("expected empty segment");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("tag"));
        }
        try {
            GameplayTag.of(".Character");
            fail("expected leading dot");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("tag"));
        }
    }

    @Test
    public void containerAddHasRemoveExactAndHierarchical() {
        GameplayTagContainer tags = new GameplayTagContainer();
        assertTrue(tags.isEmpty());
        assertTrue(tags.addTag("Character.Status.Burning"));
        assertFalse(tags.addTag(GameplayTag.of("Character.Status.Burning")));
        assertTrue(tags.hasTagExact("Character.Status.Burning"));
        assertTrue(tags.hasTag("Character.Status.Burning"));
        assertTrue(tags.hasTag("Character.Status"));
        assertTrue(tags.hasTag(GameplayTag.of("Character")));
        assertFalse(tags.hasTagExact("Character.Status"));
        assertFalse(tags.hasTag("Character.Status.Frozen"));
        assertFalse(tags.hasTag("Item.Weapon"));
        assertTrue(tags.removeTag("Character.Status.Burning"));
        assertFalse(tags.hasTag("Character"));
        assertFalse(tags.removeTag("Character.Status.Burning"));
        assertEquals(0, tags.size());
    }

    @Test
    public void containerHasAllHasAnyAndQueryMatch() {
        GameplayTagContainer tags = new GameplayTagContainer();
        tags.addTag("Character.Player");
        tags.addTag("Status.Burning");
        tags.addTag("Item.Weapon.Melee");

        GameplayTagContainer required = new GameplayTagContainer();
        required.addTag("Character");
        required.addTag("Status.Burning");
        assertTrue(tags.hasAll(required));
        assertTrue(tags.hasAny(GameplayTagContainer.of("Item.Weapon", "Status.Dead")));
        assertFalse(tags.hasAny(GameplayTagContainer.of("Status.Dead", "World.Landmark")));

        assertTrue(GameplayTagQuery.all("Character.Player", "Status").matches(tags));
        assertTrue(GameplayTagQuery.any("Item.Weapon.Ranged", "Item.Weapon.Melee").matches(tags));
        assertTrue(GameplayTagQuery.none("Status.Dead").matches(tags));
        assertFalse(GameplayTagQuery.all("Character.Player", "Status.Frozen").matches(tags));
        assertFalse(GameplayTagQuery.any("Status.Dead").matches(tags));
        assertFalse(GameplayTagQuery.none("Status.Burning").matches(tags));

        GameplayTagQuery mixed = GameplayTagQuery.builder()
                .all("Character.Player")
                .any("Item.Weapon.Melee", "Item.Weapon.Ranged")
                .none("Status.Dead")
                .build();
        assertTrue(mixed.matches(tags));
        tags.addTag("Status.Dead");
        assertFalse(mixed.matches(tags));
        assertTrue(tags.matches(GameplayTagQuery.any("Status.Dead")));
    }

    @Test
    public void actorAndDataAssetHoldTagContainers() {
        Actor actor = new Actor();
        assertTrue(actor.gameplayTags().addTag("Character.Player"));
        assertTrue(actor.gameplayTags().hasTag("Character"));

        WeaponDataAsset blade = new WeaponDataAsset("SideBlade", 10.0f, 0.2f, 6);
        blade.gameplayTags().addTag("Item.Weapon.Melee");
        assertTrue(blade.gameplayTags().hasTag("Item.Weapon"));
        assertFalse(blade.gameplayTags().hasTagExact("Item.Weapon"));
    }

    @Test
    public void hallDemoActorsAndBladeCarryGameplayTags() {
        World world = new World(AssetRegistry.withDemoAssets());
        world.openLevel("Hall");

        PlayerPawn pawn = world.actorsOf(PlayerPawn.class).get(0);
        HallBeaconActor beacon = world.actorsOf(HallBeaconActor.class).get(0);
        WeaponDataAsset blade = world.assets().requireDataAsset(
                AssetRegistry.HALL_BLADE_ID, WeaponDataAsset.class);

        assertTrue(pawn.gameplayTags().hasTag(GameplayTag.CHARACTER_PLAYER));
        assertTrue(beacon.gameplayTags().hasTag("World.Landmark"));
        assertTrue(blade.gameplayTags().hasTag("Item.Weapon.Melee"));

        assertTrue(GameplayStatics.hasTag(pawn, "Character.Player"));
        assertTrue(GameplayStatics.hasTag(blade, GameplayTag.of("Item.Weapon")));
        assertTrue(GameplayStatics.matches(
                pawn, GameplayTagQuery.all("Character").none("Status.Dead")));

        List<Actor> players = GameplayStatics.getActorsWithTag(world, "Character");
        assertEquals(1, players.size());
        assertSame(pawn, players.get(0));
        assertEquals(1, GameplayStatics.getActorsWithTag(world, "World.Landmark.Beacon").size());
        assertEquals(0, GameplayStatics.getActorsWithTag(world, "Item.Weapon").size());
    }

    @Test
    public void dumpListsGameplayTagsOnActorsAndDataAssets() {
        World world = new World(AssetRegistry.withDemoAssets());
        world.openLevel("Hall");
        StringBuilder out = new StringBuilder();
        world.appendDump(out);
        String dump = out.toString();
        assertTrue(dump.contains("gtags=Character.Player"));
        assertTrue(dump.contains("gtags=World.Landmark.Beacon"));
        assertTrue(dump.contains("type=WeaponDataAsset gtags=Item.Weapon.Melee"));
    }
}
