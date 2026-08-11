package com.nebysse.minetomesh.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SelectionTest {
    @Test
    void normalizesInclusiveBoundsAndVolume() {
        Selection selection = Selection.of(
                new BlockPoint("minecraft:overworld", 10, 80, 5),
                new BlockPoint("minecraft:overworld", -2, 64, 20));

        assertEquals(new BlockPoint("minecraft:overworld", -2, 64, 5), selection.min());
        assertEquals(new BlockPoint("minecraft:overworld", 10, 80, 20), selection.max());
        assertEquals(13L, selection.sizeX());
        assertEquals(17L, selection.sizeY());
        assertEquals(16L, selection.sizeZ());
        assertEquals(3536L, selection.volume());
        assertTrue(selection.contains(-2, 64, 5));
        assertTrue(selection.contains(10, 80, 20));
        assertEquals(new BlockPoint("minecraft:overworld", 12, 16, 15),
                selection.toLocal(new BlockPoint("minecraft:overworld", 10, 80, 20)));
    }

    @Test
    void ordersChunkSectionsByXThenZThenY() {
        List<ChunkSectionRef> refs = new ArrayList<>(List.of(
                new ChunkSectionRef(1, 0, 0),
                new ChunkSectionRef(0, 3, 1),
                new ChunkSectionRef(0, 2, 1),
                new ChunkSectionRef(0, 9, 0)));

        refs.sort(null);

        assertEquals(List.of(
                new ChunkSectionRef(0, 9, 0),
                new ChunkSectionRef(0, 2, 1),
                new ChunkSectionRef(0, 3, 1),
                new ChunkSectionRef(1, 0, 0)), refs);
    }

    @Test
    void rejectsPointsFromDifferentDimensions() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> Selection.of(
                new BlockPoint("minecraft:overworld", 0, 0, 0),
                new BlockPoint("minecraft:the_nether", 0, 0, 0)));

        assertEquals("Selection points must be in the same dimension", error.getMessage());
    }

    @Test
    void localCoordinatesMustUseTheSelectionDimension() {
        Selection selection = Selection.of(
                new BlockPoint("minecraft:overworld", 0, 0, 0),
                new BlockPoint("minecraft:overworld", 1, 1, 1));

        assertThrows(IllegalArgumentException.class,
                () -> selection.toLocal(new BlockPoint("minecraft:the_end", 0, 0, 0)));
    }
}
