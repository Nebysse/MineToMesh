package com.nebysse.minetomesh.scene;

import java.util.List;
import java.util.Objects;

public record PrimitiveData(
        List<Vertex> vertices,
        PrimitiveMode sourceMode,
        int[] streamVertexCounts,
        MaterialKey material) {
    public PrimitiveData {
        vertices = List.copyOf(vertices);
        Objects.requireNonNull(sourceMode, "sourceMode");
        streamVertexCounts = streamVertexCounts.clone();
        Objects.requireNonNull(material, "material");
        int covered = 0;
        for (int count : streamVertexCounts) {
            if (count <= 0) {
                throw new IllegalArgumentException("Stream vertex count must be positive");
            }
            covered = Math.addExact(covered, count);
        }
        if (covered != vertices.size()) {
            throw new IllegalArgumentException(
                    "Stream vertex counts cover " + covered + " of "
                            + vertices.size() + " vertices");
        }
    }

    @Override
    public int[] streamVertexCounts() {
        return streamVertexCounts.clone();
    }

    public int[] indices() {
        return TopologyConverter.convert(this, "primitive").indices();
    }

    public int gltfMode() {
        return TopologyConverter.convert(this, "primitive").gltfMode();
    }
}
