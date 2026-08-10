package com.onecuber.mcgltf.scene;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PrimitiveAccumulator {
    private final String objectId;
    private final Map<GroupKey, List<List<Vertex>>> streams = new LinkedHashMap<>();
    private boolean sealed;

    public PrimitiveAccumulator(String objectId) {
        this.objectId = Objects.requireNonNull(objectId, "objectId");
    }

    public void append(MaterialKey material, PrimitiveMode mode, List<Vertex> vertices) {
        if (sealed) {
            throw new IllegalStateException("Accumulator is already sealed");
        }
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(mode, "mode");
        List<Vertex> immutableVertices = List.copyOf(vertices);
        if (immutableVertices.isEmpty()) {
            return;
        }
        streams.computeIfAbsent(new GroupKey(material, mode), ignored -> new ArrayList<>())
                .add(immutableVertices);
    }

    public SealResult seal() {
        if (sealed) {
            throw new IllegalStateException("Accumulator is already sealed");
        }
        sealed = true;
        List<PrimitiveData> primitives = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (Map.Entry<GroupKey, List<List<Vertex>>> entry : streams.entrySet()) {
            List<Vertex> mergedVertices = new ArrayList<>();
            List<Integer> streamCounts = new ArrayList<>();
            boolean hasIndices = false;
            for (List<Vertex> stream : entry.getValue()) {
                TopologyConverter.ConvertedTopology converted = TopologyConverter.convert(
                        entry.getKey().mode(), stream.size(), objectId);
                mergedVertices.addAll(stream);
                streamCounts.add(stream.size());
                hasIndices |= converted.indices().length > 0;
                diagnostics.addAll(converted.diagnostics());
            }
            if (hasIndices) {
                primitives.add(new PrimitiveData(
                        mergedVertices,
                        entry.getKey().mode(),
                        streamCounts.stream().mapToInt(Integer::intValue).toArray(),
                        entry.getKey().material()));
            }
        }
        streams.clear();
        return new SealResult(primitives, diagnostics);
    }

    private record GroupKey(MaterialKey material, PrimitiveMode mode) {
    }

    public record SealResult(List<PrimitiveData> primitives, List<Diagnostic> diagnostics) {
        public SealResult {
            primitives = List.copyOf(primitives);
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
