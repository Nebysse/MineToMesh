package com.onecuber.mcgltf.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onecuber.mcgltf.scene.MaterialKey;
import com.onecuber.mcgltf.scene.TextureKey;
import java.util.Optional;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class RenderTypePolicyTest {
    @Test
    void recognizesCoreBlockRenderTypes() {
        RenderTypeDescriptor solid = RenderTypeInspector.inspect(RenderType.solid()).descriptor();
        RenderTypeDescriptor cutout = RenderTypeInspector.inspect(RenderType.cutout()).descriptor();
        RenderTypeDescriptor translucent = RenderTypeInspector.inspect(RenderType.translucent()).descriptor();

        assertEquals(MaterialKey.AlphaMode.OPAQUE, solid.alphaMode());
        assertEquals(MaterialKey.AlphaMode.MASK, cutout.alphaMode());
        assertEquals(0.5F, cutout.alphaCutoff().orElseThrow());
        assertEquals(MaterialKey.AlphaMode.BLEND, translucent.alphaMode());
        assertFalse(solid.discard());
    }

    @Test
    void recognizesEmissiveGlintAndDiscardedPasses() {
        RenderTypeDescriptor eyes = RenderTypeInspector.inspect(
                RenderType.eyes(ResourceLocation.withDefaultNamespace("textures/entity/enderman/enderman_eyes.png")))
                .descriptor();
        RenderTypeDescriptor glint = RenderTypeInspector.inspect(RenderType.glint()).descriptor();
        RenderTypeDescriptor text = RenderTypeInspector.inspect(
                RenderType.text(ResourceLocation.withDefaultNamespace("textures/font/ascii.png"))).descriptor();

        assertTrue(eyes.emissive());
        assertEquals(MaterialKey.BlendSemantic.ADDITIVE, eyes.blendSemantic());
        assertEquals(MaterialKey.BlendSemantic.GLINT, glint.blendSemantic());
        assertTrue(text.discard());
    }

    @Test
    void multiBufferSourceGroupsRenderTypesInFirstUseOrder() {
        CapturingMultiBufferSource buffers = new CapturingMultiBufferSource("fixture", descriptor ->
                new MaterialKey(
                        new TextureKey(TextureKey.Kind.RESOURCE,
                                descriptor.textureResourceId().orElse("mcgltf:missing"),
                                "textures/fixture.png"),
                        descriptor.alphaMode(), descriptor.alphaCutoff(), !descriptor.cull(),
                        descriptor.emissive(), descriptor.blendSemantic(),
                        MaterialKey.SamplerMode.NEAREST));
        var solid = buffers.getBuffer(RenderType.solid());
        for (int i = 0; i < 4; i++) {
            solid.addVertex(i, 0.0F, 0.0F);
        }
        var translucent = buffers.getBuffer(RenderType.translucent());
        for (int i = 0; i < 4; i++) {
            translucent.addVertex(i, 1.0F, 0.0F);
        }

        CapturingMultiBufferSource.CaptureResult result = buffers.finishAll();

        assertEquals(2, result.primitives().size());
        assertEquals(MaterialKey.AlphaMode.OPAQUE, result.primitives().get(0).material().alphaMode());
        assertEquals(MaterialKey.AlphaMode.BLEND, result.primitives().get(1).material().alphaMode());
    }

    @Test
    void mapsEverySupportedSourceModeAndDiscardsDebugModes() {
        assertEquals(com.onecuber.mcgltf.scene.PrimitiveMode.QUADS,
                RenderTypeInspector.primitiveMode(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS).orElseThrow());
        assertTrue(RenderTypeInspector.primitiveMode(
                com.mojang.blaze3d.vertex.VertexFormat.Mode.DEBUG_LINES).isEmpty());
    }
}
