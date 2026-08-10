package com.onecuber.mcgltf.capture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onecuber.mcgltf.world.BlockPoint;
import com.onecuber.mcgltf.world.Selection;
import org.junit.jupiter.api.Test;

class SelectionBlockViewPolicyTest {
    @Test
    void delegatesOnlyInsideSelectionAndLoadedChunks() {
        Selection selection = Selection.of(
                new BlockPoint("minecraft:overworld", 0, 0, 0),
                new BlockPoint("minecraft:overworld", 31, 31, 31));

        assertTrue(SelectionBlockView.shouldDelegate(selection, 1, 1, 1,
                (chunkX, chunkZ) -> true));
        assertFalse(SelectionBlockView.shouldDelegate(selection, -1, 1, 1,
                (chunkX, chunkZ) -> true));
        assertFalse(SelectionBlockView.shouldDelegate(selection, 17, 1, 1,
                (chunkX, chunkZ) -> false));
    }
}
