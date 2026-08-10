package com.onecuber.mcgltf.gltf;

import com.onecuber.mcgltf.scene.MaterialKey;
import java.util.Objects;
import java.util.Optional;

public record WrittenPrimitive(
        BinaryBufferWriter.Segment positions,
        BinaryBufferWriter.Segment normals,
        BinaryBufferWriter.Segment texCoords,
        Optional<BinaryBufferWriter.Segment> colors,
        BinaryBufferWriter.Segment indices,
        int gltfMode,
        MaterialKey material) {
    public WrittenPrimitive {
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(normals, "normals");
        Objects.requireNonNull(texCoords, "texCoords");
        Objects.requireNonNull(colors, "colors");
        Objects.requireNonNull(indices, "indices");
        Objects.requireNonNull(material, "material");
    }
}
