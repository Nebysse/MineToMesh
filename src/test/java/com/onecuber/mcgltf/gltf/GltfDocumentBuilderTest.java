package com.onecuber.mcgltf.gltf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.onecuber.mcgltf.scene.BatchCounters;
import com.onecuber.mcgltf.scene.CapturedNode;
import com.onecuber.mcgltf.scene.ColorRgba;
import com.onecuber.mcgltf.scene.MaterialKey;
import com.onecuber.mcgltf.scene.PrimitiveData;
import com.onecuber.mcgltf.scene.PrimitiveMode;
import com.onecuber.mcgltf.scene.TextureKey;
import com.onecuber.mcgltf.scene.Vec2f;
import com.onecuber.mcgltf.scene.Vec3f;
import com.onecuber.mcgltf.scene.Vertex;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GltfDocumentBuilderTest {
    @Test
    void buildsOneTriangleWithStableHierarchyAndExternalResources() throws Exception {
        PrimitiveData primitive = triangle();
        ByteArrayOutputStream binary = new ByteArrayOutputStream();
        WrittenPrimitive written;
        try (BinaryBufferWriter writer = new BinaryBufferWriter(binary)) {
            written = new WrittenPrimitive(
                    writer.writePositions(primitive.vertices()),
                    writer.writeNormals(primitive.vertices()),
                    writer.writeTexCoords(primitive.vertices()),
                    Optional.of(writer.writeColors(primitive.vertices())),
                    writer.writeIndices(primitive.indices()),
                    primitive.gltfMode(),
                    primitive.material());
        }
        CapturedNode node = new CapturedNode("section_0_0_0", CapturedNode.Kind.CHUNK,
                List.of(primitive), Map.of("chunkX", 0));
        GltfDocumentBuilder builder = new GltfDocumentBuilder(
                "sample.bin", Map.of("origin", List.of(0, 64, 0)));
        builder.addNode(node, List.of(written));

        JsonObject document = builder.finish(binary.size());

        assertEquals("2.0", document.getAsJsonObject("asset").get("version").getAsString());
        assertEquals("MineToMesh 0.5.0",
                document.getAsJsonObject("asset").get("generator").getAsString());
        assertEquals(0, document.get("scene").getAsInt());
        assertEquals(1, document.getAsJsonArray("scenes").size());
        assertEquals("sample.bin", document.getAsJsonArray("buffers").get(0)
                .getAsJsonObject().get("uri").getAsString());
        assertEquals(5, document.getAsJsonArray("accessors").size());
        assertEquals("textures/minecraft/block/stone.png",
                document.getAsJsonArray("images").get(0).getAsJsonObject().get("uri").getAsString());

        JsonArray nodes = document.getAsJsonArray("nodes");
        assertEquals("Chunks", nodes.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("BlockEntities", nodes.get(1).getAsJsonObject().get("name").getAsString());
        assertEquals("Entities", nodes.get(2).getAsJsonObject().get("name").getAsString());
        assertEquals("Placeholders", nodes.get(3).getAsJsonObject().get("name").getAsString());
        assertEquals("Overlays", nodes.get(4).getAsJsonObject().get("name").getAsString());
        assertEquals(5, nodes.get(0).getAsJsonObject().getAsJsonArray("children").get(0).getAsInt());

        JsonObject meshPrimitive = document.getAsJsonArray("meshes").get(0).getAsJsonObject()
                .getAsJsonArray("primitives").get(0).getAsJsonObject();
        int colorAccessor = meshPrimitive.getAsJsonObject("attributes").get("COLOR_0").getAsInt();
        assertTrue(document.getAsJsonArray("accessors").get(colorAccessor).getAsJsonObject()
                .get("normalized").getAsBoolean());
        assertEquals(4, meshPrimitive.get("mode").getAsInt());
        assertEquals(0, document.getAsJsonObject("extras").getAsJsonArray("origin").get(0).getAsInt());
    }

    static PrimitiveData triangle() {
        MaterialKey material = new MaterialKey(
                new TextureKey(TextureKey.Kind.RESOURCE, "minecraft:block/stone",
                        "textures/minecraft/block/stone.png"),
                MaterialKey.AlphaMode.OPAQUE,
                Optional.empty(),
                false,
                false,
                MaterialKey.BlendSemantic.STANDARD,
                MaterialKey.SamplerMode.NEAREST);
        List<Vertex> vertices = List.of(
                vertex(0, 0, 0), vertex(1, 0, 0), vertex(0, 1, 0));
        return new PrimitiveData(
                vertices, PrimitiveMode.TRIANGLES, new int[] {vertices.size()}, material);
    }

    private static Vertex vertex(float x, float y, float z) {
        return new Vertex(new Vec3f(x, y, z), Vec3f.UP,
                new Vec2f(0.0F, 0.0F), ColorRgba.WHITE);
    }
}
