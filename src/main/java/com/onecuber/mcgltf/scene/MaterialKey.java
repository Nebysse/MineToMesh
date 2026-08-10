package com.onecuber.mcgltf.scene;

import java.util.Objects;
import java.util.Optional;

public record MaterialKey(
        TextureKey texture,
        AlphaMode alphaMode,
        Optional<Float> alphaCutoff,
        boolean doubleSided,
        boolean emissive,
        BlendSemantic blendSemantic,
        SamplerMode samplerMode) {
    public MaterialKey {
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(alphaMode, "alphaMode");
        Objects.requireNonNull(alphaCutoff, "alphaCutoff");
        Objects.requireNonNull(blendSemantic, "blendSemantic");
        Objects.requireNonNull(samplerMode, "samplerMode");
        if (alphaMode == AlphaMode.MASK && alphaCutoff.isEmpty()) {
            throw new IllegalArgumentException("MASK materials require an alpha cutoff");
        }
        if (alphaMode != AlphaMode.MASK && alphaCutoff.isPresent()) {
            throw new IllegalArgumentException("Only MASK materials may define an alpha cutoff");
        }
        alphaCutoff.ifPresent(value -> {
            if (!Float.isFinite(value) || value < 0.0F || value > 1.0F) {
                throw new IllegalArgumentException("Alpha cutoff must be finite and in range 0..1");
            }
        });
    }

    public enum AlphaMode {
        OPAQUE,
        MASK,
        BLEND
    }

    public enum BlendSemantic {
        STANDARD,
        ADDITIVE,
        GLINT
    }

    public enum SamplerMode {
        NEAREST,
        NEAREST_MIPMAP
    }
}
