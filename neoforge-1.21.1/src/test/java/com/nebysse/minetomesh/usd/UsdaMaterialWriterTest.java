package com.nebysse.minetomesh.usd;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.TextureKey;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UsdaMaterialWriterTest {
    @Test
    void writesTintedMaskedEmissivePreviewNetwork() {
        MaterialKey material = new MaterialKey(
                new TextureKey(TextureKey.Kind.RESOURCE, "minecraft:block/grass",
                        "textures/minecraft/block/grass.png"),
                MaterialKey.AlphaMode.MASK, Optional.of(0.5F),
                true, true, MaterialKey.BlendSemantic.STANDARD,
                MaterialKey.SamplerMode.NEAREST);
        String usd = UsdaMaterialWriter.fragment(material);
        assertTrue(usd.contains("def Material \"" + UsdaNames.material(material) + "\""));
        assertTrue(usd.contains("UsdPreviewSurface"));
        assertTrue(usd.contains("UsdUVTexture"));
        assertTrue(usd.contains("UsdPrimvarReader_float2"));
        assertTrue(usd.contains("UsdPrimvarReader_float4"));
        assertTrue(usd.contains("inputs:scale.connect"));
        assertTrue(usd.contains("inputs:opacityThreshold = 0.5"));
        assertTrue(usd.contains("inputs:emissiveColor.connect"));
        assertTrue(usd.contains("asset inputs:file = @textures/minecraft/block/grass.png@"));
        assertTrue(usd.contains("minetomesh:samplerMode = \"NEAREST\""));
    }

    @Test
    void opaqueMaterialDoesNotConnectAlphaOrWriteCutoff() {
        String usd = UsdaMaterialWriter.fragment(material(MaterialKey.AlphaMode.OPAQUE));
        assertFalse(usd.contains("inputs:opacity.connect"));
        assertFalse(usd.contains("opacityThreshold"));
    }

    @Test
    void blendConnectsAlphaWithoutCutoff() {
        String usd = UsdaMaterialWriter.fragment(material(MaterialKey.AlphaMode.BLEND));
        assertTrue(usd.contains("inputs:opacity.connect"));
        assertFalse(usd.contains("opacityThreshold"));
    }

    private static MaterialKey material(MaterialKey.AlphaMode mode) {
        return new MaterialKey(
                new TextureKey(TextureKey.Kind.RESOURCE, "minecraft:block/stone",
                        "textures/minecraft/block/stone.png"),
                mode, Optional.empty(), false, false,
                MaterialKey.BlendSemantic.STANDARD, MaterialKey.SamplerMode.NEAREST);
    }
}
