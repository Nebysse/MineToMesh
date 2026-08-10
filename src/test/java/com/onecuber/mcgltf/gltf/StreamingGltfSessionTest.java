package com.onecuber.mcgltf.gltf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.onecuber.mcgltf.scene.BatchCounters;
import com.onecuber.mcgltf.scene.CapturedNode;
import com.onecuber.mcgltf.scene.ChunkBatch;
import com.onecuber.mcgltf.scene.PrimitiveData;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
}
