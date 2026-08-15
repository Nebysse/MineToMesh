package com.nebysse.minetomesh.scene;

import java.util.List;
import java.util.Objects;

public record ChunkBatch(
        List<CapturedNode> nodes,
        List<Diagnostic> diagnostics,
        BatchCounters counters,
        GeometryAdjustmentStats adjustments) {
    public ChunkBatch {
        nodes = List.copyOf(nodes);
        diagnostics = List.copyOf(diagnostics);
        Objects.requireNonNull(counters, "counters");
        Objects.requireNonNull(adjustments, "adjustments");
    }

    public ChunkBatch(
            List<CapturedNode> nodes,
            List<Diagnostic> diagnostics,
            BatchCounters counters) {
        this(nodes, diagnostics, counters, GeometryAdjustmentStats.ZERO);
    }
}
