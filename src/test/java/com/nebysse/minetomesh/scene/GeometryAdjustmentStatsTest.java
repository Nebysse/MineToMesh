package com.nebysse.minetomesh.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GeometryAdjustmentStatsTest {
    @Test
    void mergesCountsMaxLayerAndSortedBlockGroups() {
        GeometryAdjustmentStats grass = GeometryAdjustmentStats.forBlock(
                "minecraft:grass_block", 2, 4, 3);
        GeometryAdjustmentStats rail = GeometryAdjustmentStats.forBlock(
                "minecraft:powered_rail", 1, 1, 2);

        GeometryAdjustmentStats merged = grass.plus(rail);

        assertEquals(3, merged.coplanarGroups());
        assertEquals(5, merged.offsetFaces());
        assertEquals(3, merged.maxLayers());
        assertEquals(Map.of(
                "minecraft:grass_block", 2L,
                "minecraft:powered_rail", 1L), merged.byBlock());
        assertThrows(UnsupportedOperationException.class,
                () -> merged.byBlock().put("minecraft:stone", 1L));
    }

    @Test
    void zeroGroupFactoryReturnsZero() {
        assertEquals(GeometryAdjustmentStats.ZERO,
                GeometryAdjustmentStats.forBlock("minecraft:stone", 0, 0, 0));
    }
}
