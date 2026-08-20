package com.nebysse.minetomesh.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExportPlanTest {
    @Test
    void sortsSectionWorkAndDeduplicatesMissingChunks() {
        Selection selection = Selection.of(
                new BlockPoint("minecraft:overworld", 0, 0, 0),
                new BlockPoint("minecraft:overworld", 31, 31, 31));
        ExportPlan.SectionWork later = new ExportPlan.SectionWork(
                new ChunkSectionRef(1, 1, 0), 16, 16, 0, 31, 31, 15);
        ExportPlan.SectionWork earlier = new ExportPlan.SectionWork(
                new ChunkSectionRef(0, 0, 0), 0, 0, 0, 15, 15, 15);

        ExportPlan plan = new ExportPlan(selection,
                List.of(later, earlier),
                List.of(new ExportPlan.MissingChunk(2, 1),
                        new ExportPlan.MissingChunk(-1, 4),
                        new ExportPlan.MissingChunk(2, 1)));

        assertEquals(List.of(earlier, later), plan.sections());
        assertEquals(List.of(new ExportPlan.MissingChunk(-1, 4),
                new ExportPlan.MissingChunk(2, 1)), plan.missingChunks());
        assertEquals(2, plan.totalWorkItems());
        assertEquals(new ChunkRange(0, 1, 0, 1), plan.chunkRange());
        assertEquals(List.of(
                new ChunkCoordinate(0, 0),
                new ChunkCoordinate(0, 1),
                new ChunkCoordinate(1, 0),
                new ChunkCoordinate(1, 1)), plan.chunkCursor().next(4));
    }
}
