package com.nebysse.minetomesh.job;

import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.PrimitiveMode;
import com.nebysse.minetomesh.scene.Vertex;
import java.util.List;
import java.util.Objects;

public record RawPrimitiveStream(
        String layerGroupId,
        MaterialKey material,
        PrimitiveMode mode,
        List<Vertex> vertices) {
    public RawPrimitiveStream {
        Objects.requireNonNull(layerGroupId, "layerGroupId");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(mode, "mode");
        vertices = List.copyOf(vertices);
    }
}
