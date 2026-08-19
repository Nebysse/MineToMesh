package com.nebysse.minetomesh.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nebysse.minetomesh.world.BlockPoint;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SceneValueTest {
    @Test
    void rejectsNonFiniteVectorComponents() {
        assertThrows(IllegalArgumentException.class, () -> new Vec2f(Float.NaN, 0.0F));
        assertThrows(IllegalArgumentException.class, () -> new Vec2f(0.0F, Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new Vec3f(Float.NEGATIVE_INFINITY, 0.0F, 0.0F));
        assertThrows(IllegalArgumentException.class, () -> new Vec3f(0.0F, Float.NaN, 0.0F));
        assertThrows(IllegalArgumentException.class, () -> new Vec3f(0.0F, 0.0F, Float.POSITIVE_INFINITY));
    }

    @Test
    void normalizesVectorsAndUsesUpForZeroLength() {
        assertEquals(new Vec3f(0.6F, 0.0F, 0.8F), new Vec3f(3.0F, 0.0F, 4.0F).normalizedOrUp());
        assertEquals(new Vec3f(0.0F, 1.0F, 0.0F), new Vec3f(0.0F, 0.0F, 0.0F).normalizedOrUp());
    }

    @Test
    void acceptsByteColorsAndRejectsOutOfRangeChannels() {
        assertEquals(new ColorRgba(255, 255, 255, 255), ColorRgba.WHITE);
        assertThrows(IllegalArgumentException.class, () -> new ColorRgba(-1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ColorRgba(0, 256, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ColorRgba(0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ColorRgba(0, 0, 0, 256));
    }

    @Test
    void validatesMaterialAlphaCutoffSemantics() {
        TextureKey texture = new TextureKey(TextureKey.Kind.RESOURCE,
                "minecraft:block/stone", "textures/minecraft/block/stone.png");

        assertThrows(IllegalArgumentException.class, () -> new MaterialKey(texture,
                MaterialKey.AlphaMode.MASK, Optional.empty(), false, false,
                MaterialKey.BlendSemantic.STANDARD, MaterialKey.SamplerMode.NEAREST));
        assertThrows(IllegalArgumentException.class, () -> new MaterialKey(texture,
                MaterialKey.AlphaMode.OPAQUE, Optional.of(0.5F), false, false,
                MaterialKey.BlendSemantic.STANDARD, MaterialKey.SamplerMode.NEAREST));

        MaterialKey masked = new MaterialKey(texture,
                MaterialKey.AlphaMode.MASK, Optional.of(0.5F), true, false,
                MaterialKey.BlendSemantic.STANDARD, MaterialKey.SamplerMode.NEAREST_MIPMAP);
        assertEquals(0.5F, masked.alphaCutoff().orElseThrow());
    }

    @Test
    void diagnosticCarriesStableContextWithoutNullSentinels() {
        Diagnostic diagnostic = new Diagnostic(
                Diagnostic.Severity.WARNING,
                "UNKNOWN_RENDER_TYPE",
                "minecraft:stone",
                Optional.of(new BlockPoint("minecraft:overworld", 1, 2, 3)),
                "example.Renderer",
                "",
                "Fell back to inferred semantics");

        assertEquals("UNKNOWN_RENDER_TYPE", diagnostic.code());
        assertThrows(NullPointerException.class, () -> new Diagnostic(
                Diagnostic.Severity.WARNING, "CODE", "object", Optional.empty(), null, "", "message"));
    }

    @Test
    void vertexCarriesNormalizedCaptureFields() {
        Vertex vertex = new Vertex(
                new Vec3f(1.0F, 2.0F, 3.0F),
                new Vec3f(0.0F, 1.0F, 0.0F),
                new Vec2f(0.25F, 0.75F),
                ColorRgba.WHITE);

        assertEquals(new Vec2f(0.25F, 0.75F), vertex.uv());
        assertEquals(PrimitiveMode.QUADS, PrimitiveMode.valueOf("QUADS"));
    }
}
