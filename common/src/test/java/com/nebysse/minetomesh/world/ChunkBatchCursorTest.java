package com.nebysse.minetomesh.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkBatchCursorTest {
    @Test
    void selectionUsesFloorDivisionForNegativeBlocks() {
        Selection selection = Selection.of(
                new BlockPoint("minecraft:overworld", -17, 0, -1),
                new BlockPoint("minecraft:overworld", 16, 10, 16));

        assertEquals(new ChunkRange(-2, 1, -1, 1), ChunkRange.from(selection));
        assertEquals(12, ChunkRange.from(selection).totalChunks());
    }

    @Test
    void emitsAtMostRequestedChunksInsideOneCompactWindow() {
        ChunkBatchCursor cursor = new ChunkRange(-1, 1, 2, 4).cursor();

        assertEquals(List.of(
                new ChunkCoordinate(-1, 2),
                new ChunkCoordinate(-1, 3),
                new ChunkCoordinate(-1, 4),
                new ChunkCoordinate(0, 2)), cursor.next(4));
        assertEquals(5, cursor.remaining());
        assertEquals(4, cursor.emitted());
        assertTrue(cursor.currentBatchBounds().width() <= 4);
        assertTrue(cursor.currentBatchBounds().depth() <= 4);
        assertEquals(new ChunkCoordinate(-1, 3),
                cursor.currentBatchBounds().center());
    }

    @Test
    void batchOfSixteenNeverBecomesOneBySixteenStrip() {
        ChunkBatchCursor cursor = new ChunkRange(0, 7, 0, 7).cursor();
        List<List<ChunkCoordinate>> batches = new ArrayList<>();

        while (!cursor.exhausted()) {
            List<ChunkCoordinate> batch = cursor.next(16);
            batches.add(batch);
            assertEquals(16, batch.size());
            assertTrue(cursor.currentBatchBounds().width() <= 4);
            assertTrue(cursor.currentBatchBounds().depth() <= 4);
        }

        assertEquals(4, batches.size());
        assertEquals(new ChunkCoordinate(0, 0), batches.get(0).get(0));
        assertEquals(new ChunkCoordinate(3, 3), batches.get(0).get(15));
        assertEquals(new ChunkCoordinate(0, 4), batches.get(1).get(0));
        assertEquals(new ChunkCoordinate(4, 0), batches.get(2).get(0));
        assertEquals(new ChunkCoordinate(4, 4), batches.get(3).get(0));
    }

    @Test
    void doesNotCombineRemainderOfOneMacroWindowWithNextWindow() {
        ChunkRange range = new ChunkRange(0, 4, 0, 4);
        ChunkBatchCursor cursor = range.cursor();
        List<Integer> sizes = new ArrayList<>();

        while (!cursor.exhausted()) {
            sizes.add(cursor.next(5).size());
        }

        assertEquals(List.of(5, 5, 5, 1, 4, 4, 1), sizes);
        assertEquals(7, range.totalBatches(5));
        assertEquals(25, cursor.emitted());
        assertEquals(0, cursor.remaining());
    }

    @Test
    void separateCursorsDoNotShareState() {
        ChunkRange range = new ChunkRange(0, 3, 0, 3);
        ChunkBatchCursor first = range.cursor();
        ChunkBatchCursor second = range.cursor();

        first.next(4);

        assertEquals(4, first.emitted());
        assertEquals(0, second.emitted());
        assertFalse(second.exhausted());
    }

    @Test
    void validatesBoundsAndBatchSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChunkRange(2, 1, 0, 0));
        ChunkBatchCursor cursor = new ChunkRange(0, 0, 0, 0).cursor();
        assertThrows(IllegalArgumentException.class, () -> cursor.next(0));
        assertThrows(IllegalArgumentException.class, () -> cursor.next(17));
        assertThrows(IllegalStateException.class, cursor::currentBatchBounds);
    }

    @Test
    void emptyCursorReturnsNoMoreBatches() {
        ChunkBatchCursor cursor = new ChunkRange(0, 0, 0, 0).cursor();
        assertEquals(List.of(new ChunkCoordinate(0, 0)), cursor.next(1));
        assertTrue(cursor.exhausted());
        assertEquals(List.of(), cursor.next(1));
    }
}
