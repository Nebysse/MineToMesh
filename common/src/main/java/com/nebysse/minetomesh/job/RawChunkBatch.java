package com.nebysse.minetomesh.job;

import com.nebysse.minetomesh.scene.BatchCounters;
import com.nebysse.minetomesh.scene.Diagnostic;
import com.nebysse.minetomesh.world.ChunkCoordinate;
import java.util.List;
import java.util.Objects;

public record RawChunkBatch(
        long sequence,
        ChunkCoordinate chunk,
        List<RawCapturedObject> objects,
        List<Diagnostic> diagnostics,
        BatchCounters counters) {
    public RawChunkBatch {
        if (sequence < 0) {
            throw new IllegalArgumentException("Batch sequence must not be negative");
        }
        Objects.requireNonNull(chunk, "chunk");
        objects = List.copyOf(objects);
        diagnostics = List.copyOf(diagnostics);
        Objects.requireNonNull(counters, "counters");
    }
}
