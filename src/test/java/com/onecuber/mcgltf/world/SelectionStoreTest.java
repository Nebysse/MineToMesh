package com.onecuber.mcgltf.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SelectionStoreTest {
    @Test
    void exposesSelectionOnlyAfterBothPointsExist() {
        SelectionStore store = new SelectionStore();
        BlockPoint first = new BlockPoint("minecraft:overworld", 1, 2, 3);
        BlockPoint second = new BlockPoint("minecraft:overworld", 4, 5, 6);

        store.setPos1(first);
        assertEquals(first, store.pos1().orElseThrow());
        assertTrue(store.selection().isEmpty());

        store.setPos2(second);
        assertEquals(Selection.of(first, second), store.selection().orElseThrow());
    }

    @Test
    void clearRemovesBothPoints() {
        SelectionStore store = filledStore();

        store.clear();

        assertTrue(store.pos1().isEmpty());
        assertTrue(store.pos2().isEmpty());
        assertTrue(store.selection().isEmpty());
    }

    @Test
    void dimensionChangeClearsBothPointsOnlyWhenDimensionChanges() {
        SelectionStore store = filledStore();

        assertFalse(store.clearIfDimensionChanged("minecraft:overworld"));
        assertTrue(store.selection().isPresent());
        assertTrue(store.clearIfDimensionChanged("minecraft:the_nether"));
        assertTrue(store.selection().isEmpty());
    }

    private static SelectionStore filledStore() {
        SelectionStore store = new SelectionStore();
        store.setPos1(new BlockPoint("minecraft:overworld", 1, 2, 3));
        store.setPos2(new BlockPoint("minecraft:overworld", 4, 5, 6));
        return store;
    }
}
