package com.nebysse.minetomesh.gltf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nebysse.minetomesh.scene.BatchCounters;
import com.nebysse.minetomesh.scene.CapturedNode;
import com.nebysse.minetomesh.scene.ChunkBatch;
import com.nebysse.minetomesh.scene.ColorRgba;
import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.PrimitiveData;
import com.nebysse.minetomesh.scene.TextureKey;
import com.nebysse.minetomesh.scene.Vertex;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StreamingGltfSessionTest {
    @TempDir
    Path tempDir;

    @Test
    void streamsBatchesToExternalBinaryAndWritesDocument() throws Exception {
        PrimitiveData primitive = GltfDocumentBuilderTest.triangle();
        CapturedNode first = new CapturedNode("first", CapturedNode.Kind.CHUNK,
                List.of(primitive), Map.of());
        CapturedNode second = new CapturedNode("second", CapturedNode.Kind.ENTITY,
                List.of(primitive), Map.of());

        StreamingGltfSession.OutputStatistics statistics;
        try (StreamingGltfSession session = new StreamingGltfSession(
                tempDir, "sample", Map.of("dimension", "minecraft:overworld"))) {
            session.append(new ChunkBatch(List.of(first), List.of(), BatchCounters.ZERO));
            session.append(new ChunkBatch(List.of(second), List.of(), BatchCounters.ZERO));
            statistics = session.finish();
        }

        assertEquals(2L, statistics.nodeCount());
        assertEquals(2L, statistics.primitiveCount());
        assertEquals(Files.size(tempDir.resolve("sample.bin")), statistics.binaryByteLength());
        assertTrue(statistics.binaryByteLength() > 0);
        JsonObject document = JsonParser.parseString(
                Files.readString(tempDir.resolve("sample.gltf"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(2, document.getAsJsonArray("meshes").size());
    }

    @Test
    void omitsColorAttributeForOpaqueWhiteVertices() throws Exception {
        JsonObject document = exportPrimitive(
                "white", GltfDocumentBuilderTest.triangle());
        JsonObject attributes = firstPrimitive(document).getAsJsonObject("attributes");

        assertFalse(attributes.has("COLOR_0"));
        assertEquals(4, document.getAsJsonArray("accessors").size());
    }

    @Test
    void retainsColorAttributeForRgbTintOrVertexAlpha() throws Exception {
        JsonObject tinted = exportPrimitive(
                "tinted",
                withFirstColor(GltfDocumentBuilderTest.triangle(),
                        new ColorRgba(254, 255, 255, 255)));
        JsonObject alpha = exportPrimitive(
                "alpha",
                withFirstColor(GltfDocumentBuilderTest.triangle(),
                        new ColorRgba(255, 255, 255, 254)));

        assertTrue(firstPrimitive(tinted).getAsJsonObject("attributes").has("COLOR_0"));
        assertTrue(firstPrimitive(alpha).getAsJsonObject("attributes").has("COLOR_0"));
    }

    @Test
    void coalescesSelectionOverlayFragmentsIntoOneTintedMaskedNodeAndMesh() throws Exception {
        PrimitiveData primitive = withMaterial(
                withFirstColor(GltfDocumentBuilderTest.triangle(),
                        new ColorRgba(80, 160, 40, 255)),
                overlayMaterial());
        Map<String, Object> extras = Map.of(
                "layerRole", "grass_side_overlay",
                "scope", "selection",
                "sourceTexture", "minecraft:block/grass_block_side_overlay");
        CapturedNode first = new CapturedNode(
                "selection/grass_side_overlay", CapturedNode.Kind.OVERLAY,
                List.of(primitive), extras);
        CapturedNode second = new CapturedNode(
                "selection/grass_side_overlay", CapturedNode.Kind.OVERLAY,
                List.of(primitive), extras);

        StreamingGltfSession.OutputStatistics statistics;
        try (StreamingGltfSession session = new StreamingGltfSession(
                tempDir, "overlay", Map.of())) {
            session.append(new ChunkBatch(List.of(first), List.of(), BatchCounters.ZERO));
            session.append(new ChunkBatch(List.of(second), List.of(), BatchCounters.ZERO));
            statistics = session.finish();
        }

        JsonObject document = JsonParser.parseString(
                Files.readString(tempDir.resolve("overlay.gltf"))).getAsJsonObject();
        assertEquals(1L, statistics.nodeCount());
        assertEquals(2L, statistics.primitiveCount());
        assertEquals(1, document.getAsJsonArray("meshes").size());
        JsonArray primitives = document.getAsJsonArray("meshes").get(0)
                .getAsJsonObject().getAsJsonArray("primitives");
        assertEquals(2, primitives.size());
        assertTrue(primitives.get(0).getAsJsonObject()
                .getAsJsonObject("attributes").has("COLOR_0"));
        int materialIndex = primitives.get(0).getAsJsonObject().get("material").getAsInt();
        assertEquals("MASK", document.getAsJsonArray("materials").get(materialIndex)
                .getAsJsonObject().get("alphaMode").getAsString());
        JsonArray nodes = document.getAsJsonArray("nodes");
        long matchingNodes = StreamSupport.stream(nodes.spliterator(), false)
                .map(JsonElement::getAsJsonObject)
                .filter(node -> node.has("name")
                        && node.get("name").getAsString()
                        .equals("selection/grass_side_overlay"))
                .count();
        assertEquals(1L, matchingNodes);
    }

    @Test
    void rejectsOverlayFragmentsWithConflictingExtras() throws Exception {
        PrimitiveData primitive = GltfDocumentBuilderTest.triangle();
        CapturedNode first = new CapturedNode(
                "selection/grass_side_overlay", CapturedNode.Kind.OVERLAY,
                List.of(primitive), Map.of("scope", "selection"));
        CapturedNode conflicting = new CapturedNode(
                "selection/grass_side_overlay", CapturedNode.Kind.OVERLAY,
                List.of(primitive), Map.of("scope", "section"));

        try (StreamingGltfSession session = new StreamingGltfSession(
                tempDir, "conflicting-overlay", Map.of())) {
            session.append(new ChunkBatch(List.of(first), List.of(), BatchCounters.ZERO));
            assertThrows(IllegalArgumentException.class,
                    () -> session.append(new ChunkBatch(
                            List.of(conflicting), List.of(), BatchCounters.ZERO)));
        }
    }

    @Test
    void internalValidatorAcceptsCompleteDocumentAndRejectsMissingResources() throws Exception {
        PrimitiveData primitive = GltfDocumentBuilderTest.triangle();
        CapturedNode node = new CapturedNode("first", CapturedNode.Kind.CHUNK,
                List.of(primitive), Map.of());
        JsonObject document;
        try (StreamingGltfSession session = new StreamingGltfSession(tempDir, "valid", Map.of())) {
            session.append(new ChunkBatch(List.of(node), List.of(), BatchCounters.ZERO));
            session.finish();
        }
        Path texture = tempDir.resolve("textures/minecraft/block/stone.png");
        Files.createDirectories(texture.getParent());
        Files.write(texture, new byte[] {1, 2, 3});
        document = JsonParser.parseString(Files.readString(tempDir.resolve("valid.gltf"))).getAsJsonObject();

        assertTrue(InternalGltfValidator.validate(document, tempDir).isEmpty());
        Files.delete(texture);
        assertTrue(InternalGltfValidator.validate(document, tempDir).stream()
                .anyMatch(message -> message.contains("Missing external resource")));
    }

    private JsonObject exportPrimitive(String name, PrimitiveData primitive) throws Exception {
        CapturedNode node = new CapturedNode(
                name, CapturedNode.Kind.ENTITY, List.of(primitive), Map.of());
        try (StreamingGltfSession session = new StreamingGltfSession(
                tempDir, name, Map.of())) {
            session.append(new ChunkBatch(List.of(node), List.of(), BatchCounters.ZERO));
            session.finish();
        }
        return JsonParser.parseString(Files.readString(tempDir.resolve(name + ".gltf")))
                .getAsJsonObject();
    }

    private static JsonObject firstPrimitive(JsonObject document) {
        return document.getAsJsonArray("meshes").get(0).getAsJsonObject()
                .getAsJsonArray("primitives").get(0).getAsJsonObject();
    }

    private static MaterialKey overlayMaterial() {
        return new MaterialKey(
                new TextureKey(
                        TextureKey.Kind.ATLAS_SPRITE,
                        "minecraft:block/grass_block_side_overlay",
                        "textures/minecraft/block/grass_block_side_overlay.png"),
                MaterialKey.AlphaMode.MASK,
                Optional.of(0.5F),
                true,
                false,
                MaterialKey.BlendSemantic.STANDARD,
                MaterialKey.SamplerMode.NEAREST);
    }

    private static PrimitiveData withMaterial(
            PrimitiveData source,
            MaterialKey material) {
        return new PrimitiveData(
                source.vertices(),
                source.sourceMode(),
                source.streamVertexCounts(),
                material);
    }

    private static PrimitiveData withFirstColor(
            PrimitiveData source,
            ColorRgba color) {
        List<Vertex> vertices = new ArrayList<>(source.vertices());
        Vertex first = vertices.getFirst();
        vertices.set(0, new Vertex(
                first.position(), first.normal(), first.uv(), color));
        return new PrimitiveData(
                vertices,
                source.sourceMode(),
                source.streamVertexCounts(),
                source.material());
    }
}
