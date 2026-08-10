package com.onecuber.mcgltf.scene;

import java.nio.file.Path;
import java.util.Objects;

public record TextureKey(Kind kind, String sourceId, String outputPath) {
    public TextureKey {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(outputPath, "outputPath");
        if (sourceId.isEmpty()) {
            throw new IllegalArgumentException("Texture source ID must not be empty");
        }
        Path path = Path.of(outputPath);
        if (outputPath.isEmpty() || path.isAbsolute() || outputPath.indexOf('\\') >= 0
                || path.normalize().startsWith("..")) {
            throw new IllegalArgumentException("Texture output path must be a contained relative path");
        }
    }

    public enum Kind {
        ATLAS_SPRITE,
        RESOURCE,
        DYNAMIC,
        GLINT
    }
}
