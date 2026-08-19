package com.nebysse.minetomesh.capture;

import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.PrimitiveMode;
import java.util.Objects;
import java.util.Optional;

public record RenderTypeDescriptor(
        String name,
        PrimitiveMode primitiveMode,
        Optional<String> textureResourceId,
        MaterialKey.AlphaMode alphaMode,
        Optional<Float> alphaCutoff,
        boolean cull,
        boolean emissive,
        MaterialKey.BlendSemantic blendSemantic,
        boolean mipmap,
        boolean discard) {
    public RenderTypeDescriptor {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(primitiveMode, "primitiveMode");
        Objects.requireNonNull(textureResourceId, "textureResourceId");
        Objects.requireNonNull(alphaMode, "alphaMode");
        Objects.requireNonNull(alphaCutoff, "alphaCutoff");
        Objects.requireNonNull(blendSemantic, "blendSemantic");
    }
}
