package com.onecuber.mcgltf.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.onecuber.mcgltf.capture.RenderTypeDescriptor;
import com.onecuber.mcgltf.scene.MaterialKey;
import com.onecuber.mcgltf.scene.PrimitiveMode;
import com.onecuber.mcgltf.scene.TextureKey;
import com.onecuber.mcgltf.texture.TextureImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MaterialResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void mapsApprovedRenderSemanticsToMaterialKeys() {
        TextureKey texture = new TextureKey(TextureKey.Kind.RESOURCE,
                "minecraft:block/stone", "textures/minecraft/block/stone.png");

        MaterialKey solid = MaterialResolver.resolve(descriptor("solid", MaterialKey.AlphaMode.OPAQUE,
                Optional.empty(), true, false, MaterialKey.BlendSemantic.STANDARD, true), texture);
        MaterialKey cutout = MaterialResolver.resolve(descriptor("cutout", MaterialKey.AlphaMode.MASK,
                Optional.of(0.5F), false, false, MaterialKey.BlendSemantic.STANDARD, false), texture);
        MaterialKey translucent = MaterialResolver.resolve(descriptor("translucent", MaterialKey.AlphaMode.BLEND,
                Optional.empty(), true, false, MaterialKey.BlendSemantic.STANDARD, false), texture);
        MaterialKey emissive = MaterialResolver.resolve(descriptor("eyes", MaterialKey.AlphaMode.BLEND,
                Optional.empty(), false, true, MaterialKey.BlendSemantic.ADDITIVE, false), texture);
        MaterialKey glint = MaterialResolver.resolve(descriptor("glint", MaterialKey.AlphaMode.BLEND,
                Optional.empty(), false, false, MaterialKey.BlendSemantic.GLINT, false), texture);

        assertEquals(MaterialKey.AlphaMode.OPAQUE, solid.alphaMode());
        assertEquals(MaterialKey.SamplerMode.NEAREST_MIPMAP, solid.samplerMode());
        assertTrue(cutout.doubleSided());
        assertEquals(0.5F, cutout.alphaCutoff().orElseThrow());
        assertEquals(MaterialKey.AlphaMode.BLEND, translucent.alphaMode());
        assertTrue(emissive.emissive());
        assertEquals(MaterialKey.BlendSemantic.ADDITIVE, emissive.blendSemantic());
        assertEquals(MaterialKey.BlendSemantic.GLINT, glint.blendSemantic());
    }

    @Test
    void writesIndependentMaterialSidecar() throws Exception {
        TextureKey texture = new TextureKey(TextureKey.Kind.RESOURCE,
                "minecraft:block/stone", "textures/minecraft/block/stone.png");
        RenderTypeDescriptor descriptor = descriptor("solid", MaterialKey.AlphaMode.OPAQUE,
                Optional.empty(), true, false, MaterialKey.BlendSemantic.STANDARD, true);
        MaterialKey material = MaterialResolver.resolve(descriptor, texture);
        MaterialSidecarWriter.MaterialRecord record = new MaterialSidecarWriter.MaterialRecord(
                3, material, descriptor,
                Optional.of(new TextureImage.AnimationInfo(16, 16, List.of(0, 1), List.of(2, 2), false)),
                List.of("ANIMATION_FIRST_FRAME_ONLY"));

        Path output = MaterialSidecarWriter.write(tempDir, record);
        JsonObject json = JsonParser.parseString(Files.readString(output)).getAsJsonObject();

        assertEquals(1, json.get("schemaVersion").getAsInt());
        assertEquals(3, json.get("gltfMaterialIndex").getAsInt());
        assertEquals("minecraft:block/stone", json.get("sourceTexture").getAsString());
        assertEquals("solid", json.get("renderType").getAsString());
        assertEquals("ANIMATION_FIRST_FRAME_ONLY",
                json.getAsJsonArray("degradationCodes").get(0).getAsString());
        assertFalse(json.getAsJsonObject("animation").get("interpolate").getAsBoolean());
    }

    private static RenderTypeDescriptor descriptor(
            String name,
            MaterialKey.AlphaMode alphaMode,
            Optional<Float> cutoff,
            boolean cull,
            boolean emissive,
            MaterialKey.BlendSemantic blend,
            boolean mipmap) {
        return new RenderTypeDescriptor(name, PrimitiveMode.QUADS,
                Optional.of("minecraft:block/stone"), alphaMode, cutoff,
                cull, emissive, blend, mipmap, false);
    }
}
