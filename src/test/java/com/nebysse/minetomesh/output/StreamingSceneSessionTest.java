package com.nebysse.minetomesh.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nebysse.minetomesh.scene.BatchCounters;
import com.nebysse.minetomesh.scene.CapturedNode;
import com.nebysse.minetomesh.scene.ChunkBatch;
import com.nebysse.minetomesh.scene.ColorRgba;
import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.PrimitiveData;
import com.nebysse.minetomesh.scene.PrimitiveMode;
import com.nebysse.minetomesh.scene.TextureKey;
import com.nebysse.minetomesh.scene.Vec2f;
import com.nebysse.minetomesh.scene.Vec3f;
import com.nebysse.minetomesh.scene.Vertex;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StreamingSceneSessionTest {
    @TempDir
    Path tempDir;

    @Test
    void identicalBatchProducesBothFormats() throws Exception {
        try (StreamingSceneSession session = new StreamingSceneSession(
                tempDir, "sample", Map.of("dimension", "minecraft:overworld"))) {
            session.append(batchWithOneQuad());
            StreamingSceneSession.OutputStatistics output = session.finish();
            assertEquals(1, output.gltf().primitiveCount());
            assertEquals(1, output.obj().primitiveCount());
        }

        assertTrue(Files.exists(tempDir.resolve("sample.gltf")));
        assertTrue(Files.exists(tempDir.resolve("sample.bin")));
        assertTrue(Files.exists(tempDir.resolve("sample.obj")));
        assertTrue(Files.exists(tempDir.resolve("sample.mtl")));
    }

    private static ChunkBatch batchWithOneQuad() {
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
                vertex(0, 0), vertex(1, 0), vertex(1, 1), vertex(0, 1));
        PrimitiveData primitive = new PrimitiveData(
                vertices, PrimitiveMode.QUADS, new int[] {4}, material);
        CapturedNode node = new CapturedNode(
                "quad", CapturedNode.Kind.CHUNK, List.of(primitive), Map.of());
        return new ChunkBatch(List.of(node), List.of(), BatchCounters.ZERO);
    }

    private static Vertex vertex(float x, float y) {
        return new Vertex(new Vec3f(x, y, 0.0F), Vec3f.UP,
                new Vec2f(x, y), ColorRgba.WHITE);
    }
}
