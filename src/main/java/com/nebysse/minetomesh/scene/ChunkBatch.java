package com.nebysse.minetomesh.scene;

import java.util.List;
import java.util.Objects;

public record ChunkBatch(
        List<CapturedNode> nodes,
        List<Diagnostic> diagnostics,
        BatchCounters counters) {
    public ChunkBatch {
        nodes = List.copyOf(nodes);
        diagnostics = List.copyOf(diagnostics);
        Objects.requireNonNull(counters, "counters");
    }
}
