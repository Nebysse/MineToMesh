package com.onecuber.mcgltf.scene;

import java.util.List;
import java.util.Objects;

public record PrimitiveData(List<Vertex> vertices, int[] indices, int gltfMode, MaterialKey material) {
    public PrimitiveData {
        vertices = List.copyOf(vertices);
        indices = indices.clone();
        Objects.requireNonNull(material, "material");
        for (int index : indices) {
            if (index < 0 || index >= vertices.size()) {
                throw new IllegalArgumentException("Primitive index out of bounds: " + index);
            }
        }
    }

    @Override
    public int[] indices() {
        return indices.clone();
    }
}
