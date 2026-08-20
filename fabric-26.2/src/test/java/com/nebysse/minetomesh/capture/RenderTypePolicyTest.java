package com.nebysse.minetomesh.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.PrimitiveTopology;
import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.TextureKey;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class RenderTypePolicyTest {
    @Test
    void recognizesCoreBlockRenderTypes() {
        RenderTypeDescriptor solid = RenderTypeInspector.inspect(
                RenderTypes.solidMovingBlock()).descriptor();
        RenderTypeDescriptor cutout = RenderTypeInspector.inspect(
                RenderTypes.cutoutMovingBlock()).descriptor();
        RenderTypeDescriptor translucent = RenderTypeInspector.inspect(
                RenderTypes.translucentMovingBlock()).descriptor();

        assertEquals(MaterialKey.AlphaMode.OPAQUE, solid.alphaMode());
        assertEquals(MaterialKey.AlphaMode.MASK, cutout.alphaMode());
        assertEquals(0.5F, cutout.alphaCutoff().orElseThrow());
        assertEquals(MaterialKey.AlphaMode.BLEND, translucent.alphaMode());
        assertFalse(solid.discard());
    }

    @Test
    void recognizesTextureEmissiveGlintAndDiscardedPasses() {
        Identifier eyesTexture = Identifier.withDefaultNamespace(
                "textures/entity/enderman/enderman_eyes.png");
        RenderTypeDescriptor eyes = RenderTypeInspector.inspect(
                RenderTypes.eyes(eyesTexture)).descriptor();
        RenderTypeDescriptor glint = RenderTypeInspector.inspect(
                RenderTypes.glint()).descriptor();
        RenderTypeDescriptor text = RenderTypeInspector.inspect(
                RenderTypes.text(Identifier.withDefaultNamespace(
                        "textures/font/ascii.png"))).descriptor();

        assertEquals(eyesTexture.toString(),
                eyes.textureResourceId().orElseThrow());
        assertTrue(eyes.emissive());
        assertEquals(MaterialKey.BlendSemantic.ADDITIVE,
                eyes.blendSemantic());
        assertEquals(MaterialKey.BlendSemantic.GLINT,
                glint.blendSemantic());
        assertTrue(text.discard());
    }

    @Test
    void multiBufferSourceGroupsRenderTypesInFirstUseOrder() {
        CapturingMultiBufferSource buffers = new CapturingMultiBufferSource(
                "fixture", descriptor -> new MaterialKey(
                        new TextureKey(
                                TextureKey.Kind.RESOURCE,
                                descriptor.textureResourceId()
                                        .orElse("minetomesh:missing"),
                                "textures/fixture.png"),
                        descriptor.alphaMode(), descriptor.alphaCutoff(),
                        !descriptor.cull(), descriptor.emissive(),
                        descriptor.blendSemantic(),
                        MaterialKey.SamplerMode.NEAREST));
        var solid = buffers.getBuffer(RenderTypes.solidMovingBlock());
        for (int i = 0; i < 4; i++) {
            solid.addVertex(i, 0.0F, 0.0F);
        }
        var translucent = buffers.getBuffer(
                RenderTypes.translucentMovingBlock());
        for (int i = 0; i < 4; i++) {
            translucent.addVertex(i, 1.0F, 0.0F);
        }

        CapturingMultiBufferSource.CaptureResult result = buffers.finishAll();

        assertEquals(2, result.primitives().size());
        assertEquals(MaterialKey.AlphaMode.OPAQUE,
                result.primitives().get(0).material().alphaMode());
        assertEquals(MaterialKey.AlphaMode.BLEND,
                result.primitives().get(1).material().alphaMode());
    }

    @Test
    void mapsEverySupportedSourceModeAndDiscardsDebugModes() {
        assertEquals(com.nebysse.minetomesh.scene.PrimitiveMode.QUADS,
                RenderTypeInspector.primitiveMode(
                        PrimitiveTopology.QUADS).orElseThrow());
        assertTrue(RenderTypeInspector.primitiveMode(
                PrimitiveTopology.DEBUG_LINES).isEmpty());
    }
}
