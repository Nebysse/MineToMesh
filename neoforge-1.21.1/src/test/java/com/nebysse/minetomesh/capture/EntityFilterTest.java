package com.nebysse.minetomesh.capture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class EntityFilterTest {
    private static final AABB SELECTION = new AABB(0, 0, 0, 2, 2, 2);
    private static final AABB INTERSECTING = new AABB(1, 1, 1, 3, 3, 3);

    @Test
    void excludesPlayersWhenPlayerExportIsDisabledAndRejectsRemovedOrOutOfBoundsEntities() {
        assertFalse(EntityCapture.shouldInclude(
                EntityCapture.EntityCategory.PLAYER, false, false, INTERSECTING, SELECTION));
        assertTrue(EntityCapture.shouldInclude(
                EntityCapture.EntityCategory.PLAYER, true, false, INTERSECTING, SELECTION));
        assertFalse(EntityCapture.shouldInclude(
                EntityCapture.EntityCategory.PLAYER, true, true, INTERSECTING, SELECTION));
        assertFalse(EntityCapture.shouldInclude(
                EntityCapture.EntityCategory.LIVING, false, true, INTERSECTING, SELECTION));
        assertFalse(EntityCapture.shouldInclude(
                EntityCapture.EntityCategory.ITEM, false, false,
                new AABB(3, 3, 3, 4, 4, 4), SELECTION));
    }

    @Test
    void includesSupportedEntityKindsWhenTheirBoxesIntersect() {
        assertTrue(EntityCapture.shouldInclude(
                EntityCapture.EntityCategory.LIVING, false, false, INTERSECTING, SELECTION));
        assertTrue(EntityCapture.shouldInclude(
                EntityCapture.EntityCategory.VEHICLE, false, false, INTERSECTING, SELECTION));
        assertTrue(EntityCapture.shouldInclude(
                EntityCapture.EntityCategory.ARMOR_STAND, false, false, INTERSECTING, SELECTION));
        assertTrue(EntityCapture.shouldInclude(
                EntityCapture.EntityCategory.ITEM, false, false, INTERSECTING, SELECTION));
    }
}
