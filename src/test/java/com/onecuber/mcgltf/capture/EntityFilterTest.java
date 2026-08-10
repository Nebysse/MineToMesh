package com.onecuber.mcgltf.capture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class EntityFilterTest {
    private static final AABB SELECTION = new AABB(0, 0, 0, 2, 2, 2);
    private static final AABB INTERSECTING = new AABB(1, 1, 1, 3, 3, 3);

    @Test
    void excludesPlayersRemovedEntitiesAndNonIntersectingBoxes() {
        assertFalse(EntityCapture.shouldInclude(
                EntityCapture.EntityCategory.PLAYER, false, INTERSECTING, SELECTION));
        assertFalse(EntityCapture.shouldInclude(
                EntityCapture.EntityCategory.LIVING, true, INTERSECTING, SELECTION));
        assertFalse(EntityCapture.shouldInclude(
                EntityCapture.EntityCategory.ITEM, false,
                new AABB(3, 3, 3, 4, 4, 4), SELECTION));
    }

    @Test
    void includesSupportedEntityKindsWhenTheirBoxesIntersect() {
        assertTrue(EntityCapture.shouldInclude(
                EntityCapture.EntityCategory.LIVING, false, INTERSECTING, SELECTION));
        assertTrue(EntityCapture.shouldInclude(
                EntityCapture.EntityCategory.VEHICLE, false, INTERSECTING, SELECTION));
        assertTrue(EntityCapture.shouldInclude(
                EntityCapture.EntityCategory.ARMOR_STAND, false, INTERSECTING, SELECTION));
        assertTrue(EntityCapture.shouldInclude(
                EntityCapture.EntityCategory.ITEM, false, INTERSECTING, SELECTION));
    }
}
