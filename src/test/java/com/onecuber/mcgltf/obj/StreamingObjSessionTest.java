package com.onecuber.mcgltf.obj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onecuber.mcgltf.scene.BatchCounters;
import com.onecuber.mcgltf.scene.CapturedNode;
import com.onecuber.mcgltf.scene.ChunkBatch;
import com.onecuber.mcgltf.scene.PrimitiveData;
import com.onecuber.mcgltf.scene.PrimitiveMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StreamingObjSessionTest {
    @TempDir
    Path tempDir;

    @Test
    void writesQuadObjectMaterialAndSharedTexturePath() throws Exception {
        PrimitiveData quad = new PrimitiveData(
                ObjTopologyConverterTest.vertices(4),
                PrimitiveMode.QUADS,
                new int[] {4},
                ObjTopologyConverterTest.material());
        CapturedNode node = new CapturedNode(
                "chunk/0/0/section/4",
                CapturedNode.Kind.CHUNK,
                List.of(quad),
                Map.of());

        StreamingObjSession.OutputStatistics statistics;
        try (StreamingObjSession session = new StreamingObjSession(tempDir, "sample")) {
            session.append(new ChunkBatch(
                    List.of(node), List.of(), BatchCounters.ZERO));
            statistics = session.finish();
        }

        String obj = Files.readString(tempDir.resolve("sample.obj"));
        String mtl = Files.readString(tempDir.resolve("sample.mtl"));
        assertTrue(obj.contains("mtllib sample.mtl"));
        assertTrue(obj.contains("o chunk_0_0_section_4"));
        assertTrue(obj.contains("f 1/1/1 4/4/4 3/3/3 2/2/2"));
        assertTrue(mtl.contains("map_Kd textures/minecraft/block/stone.png"));
        assertEquals(1, statistics.nodeCount());
        assertEquals(1, statistics.primitiveCount());
        assertEquals(1, statistics.faceCount());
        assertEquals(0, statistics.lineCount());
    }

    @Test
    void sanitizesNonAsciiAndEmptyNamesDeterministically() {
        assertEquals("chunk_1", ObjNames.sanitize("区段/chunk 1"));
        assertEquals("unnamed", ObjNames.sanitize("空白"));
        assertEquals("safe.name-1", ObjNames.sanitize("safe.name-1"));
    }
}
