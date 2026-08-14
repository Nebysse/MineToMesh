package com.nebysse.minetomesh.obj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nebysse.minetomesh.scene.BatchCounters;
import com.nebysse.minetomesh.scene.CapturedNode;
import com.nebysse.minetomesh.scene.ChunkBatch;
import com.nebysse.minetomesh.scene.ColorRgba;
import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.PrimitiveData;
import com.nebysse.minetomesh.scene.PrimitiveMode;
import com.nebysse.minetomesh.scene.TextureKey;
import com.nebysse.minetomesh.scene.Vertex;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        assertTrue(obj.contains("f 1/1/1 2/2/2 3/3/3 4/4/4"));
        assertTrue(mtl.contains("map_Kd textures/minecraft/block/stone.png"));
        assertEquals(1, statistics.nodeCount());
        assertEquals(1, statistics.primitiveCount());
        assertEquals(1, statistics.faceCount());
        assertEquals(0, statistics.lineCount());
    }

    @Test
    void globallyBatchesBlockEntitiesWithTheSameMaterial() throws Exception {
        PrimitiveData triangle = triangle(ObjTopologyConverterTest.material());
        CapturedNode first = new CapturedNode(
                "create:belt/10,64,20", CapturedNode.Kind.BLOCK_ENTITY,
                List.of(triangle), Map.of());
        CapturedNode second = new CapturedNode(
                "create:belt/11,64,20", CapturedNode.Kind.BLOCK_ENTITY,
                List.of(triangle), Map.of());

        StreamingObjSession.OutputStatistics statistics;
        try (StreamingObjSession session = new StreamingObjSession(
                tempDir, "batched-block-entities")) {
            session.append(new ChunkBatch(List.of(first), List.of(), BatchCounters.ZERO));
            session.append(new ChunkBatch(List.of(second), List.of(), BatchCounters.ZERO));
            statistics = session.finish();
        }

        String obj = Files.readString(tempDir.resolve("batched-block-entities.obj"));
        assertEquals(1, occurrences(obj, "o BlockEntities_m0000"));
        assertEquals(2, occurrences(obj, "f -3/-3/-3 -2/-2/-2 -1/-1/-1"));
        assertFalse(obj.contains("o create_belt_10_64_20"));
        assertFalse(obj.contains("o create_belt_11_64_20"));
        assertEquals(1L, statistics.nodeCount());
        assertEquals(2L, statistics.primitiveCount());
        assertFalse(hasBlockEntitySpool());
    }

    @Test
    void separatesBlockEntityMaterialsAndKeepsOrdinaryEntitiesIndependent() throws Exception {
        PrimitiveData stone = triangle(ObjTopologyConverterTest.material());
        PrimitiveData dirt = triangle(material(
                "minecraft:block/dirt", "textures/minecraft/block/dirt.png"));
        CapturedNode stoneBlockEntity = new CapturedNode(
                "create:belt/10,64,20", CapturedNode.Kind.BLOCK_ENTITY,
                List.of(stone), Map.of());
        CapturedNode dirtBlockEntity = new CapturedNode(
                "create:belt/11,64,20", CapturedNode.Kind.BLOCK_ENTITY,
                List.of(dirt), Map.of());
        CapturedNode firstEntity = new CapturedNode(
                "minecraft:pig/first", CapturedNode.Kind.ENTITY,
                List.of(stone), Map.of());
        CapturedNode secondEntity = new CapturedNode(
                "minecraft:pig/second", CapturedNode.Kind.ENTITY,
                List.of(stone), Map.of());

        StreamingObjSession.OutputStatistics statistics;
        try (StreamingObjSession session = new StreamingObjSession(tempDir, "batch-boundaries")) {
            session.append(new ChunkBatch(
                    List.of(stoneBlockEntity, dirtBlockEntity, firstEntity, secondEntity),
                    List.of(), BatchCounters.ZERO));
            statistics = session.finish();
        }

        String obj = Files.readString(tempDir.resolve("batch-boundaries.obj"));
        assertEquals(1, occurrences(obj, "o BlockEntities_m0000"));
        assertEquals(1, occurrences(obj, "o BlockEntities_m0001"));
        assertEquals(1, occurrences(obj, "o minecraft_pig_first"));
        assertEquals(1, occurrences(obj, "o minecraft_pig_second"));
        assertEquals(4L, statistics.nodeCount());
    }

    @Test
    void closeWithoutFinishDeletesBlockEntityMaterialSpools() throws Exception {
        PrimitiveData triangle = triangle(ObjTopologyConverterTest.material());
        CapturedNode blockEntity = new CapturedNode(
                "create:belt/10,64,20", CapturedNode.Kind.BLOCK_ENTITY,
                List.of(triangle), Map.of());

        try (StreamingObjSession session = new StreamingObjSession(
                tempDir, "cancelled-block-entities")) {
            session.append(new ChunkBatch(
                    List.of(blockEntity), List.of(), BatchCounters.ZERO));
            assertTrue(hasBlockEntitySpool());
        }

        assertFalse(hasBlockEntitySpool());
    }

    @Test
    void appendsAllOverlayFragmentsUnderOneObjectAndDeletesSpool() throws Exception {
        List<Vertex> vertices = new ArrayList<>(ObjTopologyConverterTest.vertices(4));
        Vertex first = vertices.getFirst();
        vertices.set(0, new Vertex(
                first.position(), first.normal(), first.uv(),
                new ColorRgba(255, 0, 0, 255)));
        PrimitiveData quad = new PrimitiveData(
                vertices, PrimitiveMode.QUADS,
                new int[] {4}, ObjTopologyConverterTest.material());
        Map<String, Object> extras = Map.of(
                "layerRole", "grass_side_overlay",
                "scope", "selection",
                "sourceTexture", "minecraft:block/grass_block_side_overlay");
        CapturedNode overlay = new CapturedNode(
                "selection/grass_side_overlay", CapturedNode.Kind.OVERLAY,
                List.of(quad), extras);

        StreamingObjSession.OutputStatistics statistics;
        try (StreamingObjSession session = new StreamingObjSession(tempDir, "overlay")) {
            session.append(new ChunkBatch(List.of(overlay), List.of(), BatchCounters.ZERO));
            session.append(new ChunkBatch(List.of(overlay), List.of(), BatchCounters.ZERO));
            statistics = session.finish();
        }

        String obj = Files.readString(tempDir.resolve("overlay.obj"));
        assertEquals(1, occurrences(obj, "o selection_grass_side_overlay"));
        assertEquals(2, occurrences(obj,
                "f -4/-4/-4 -3/-3/-3 -2/-2/-2 -1/-1/-1"));
        assertTrue(obj.contains("v 0.0 0.0 0.0 1.0 0.0 0.0"));
        assertEquals(1L, statistics.nodeCount());
        assertEquals(2L, statistics.primitiveCount());
        assertFalse(Files.exists(tempDir.resolve(".overlay-grass-overlay.objpart")));
    }

    @Test
    void closeWithoutFinishDeletesOverlaySpool() throws Exception {
        PrimitiveData quad = new PrimitiveData(
                ObjTopologyConverterTest.vertices(4), PrimitiveMode.QUADS,
                new int[] {4}, ObjTopologyConverterTest.material());
        CapturedNode overlay = new CapturedNode(
                "selection/grass_side_overlay", CapturedNode.Kind.OVERLAY,
                List.of(quad), Map.of("scope", "selection"));
        Path spool = tempDir.resolve(".cancelled-grass-overlay.objpart");

        try (StreamingObjSession session = new StreamingObjSession(tempDir, "cancelled")) {
            session.append(new ChunkBatch(List.of(overlay), List.of(), BatchCounters.ZERO));
            assertTrue(Files.exists(spool));
        }

        assertFalse(Files.exists(spool));
    }

    @Test
    void sanitizesNonAsciiAndEmptyNamesDeterministically() {
        assertEquals("chunk_1", ObjNames.sanitize("区段/chunk 1"));
        assertEquals("unnamed", ObjNames.sanitize("空白"));
        assertEquals("safe.name-1", ObjNames.sanitize("safe.name-1"));
    }

    private PrimitiveData triangle(MaterialKey material) {
        return new PrimitiveData(
                ObjTopologyConverterTest.vertices(3),
                PrimitiveMode.TRIANGLES,
                new int[] {3},
                material);
    }

    private static MaterialKey material(String sourceId, String outputPath) {
        return new MaterialKey(
                new TextureKey(TextureKey.Kind.RESOURCE, sourceId, outputPath),
                MaterialKey.AlphaMode.OPAQUE,
                Optional.empty(),
                false,
                false,
                MaterialKey.BlendSemantic.STANDARD,
                MaterialKey.SamplerMode.NEAREST);
    }

    private boolean hasBlockEntitySpool() throws Exception {
        try (var files = Files.list(tempDir)) {
            return files.anyMatch(path -> path.getFileName().toString()
                    .matches("\\..*-block-entities-m\\d{4}\\.objpart"));
        }
    }

    private static int occurrences(String text, String token) {
        return (text.length() - text.replace(token, "").length()) / token.length();
    }
}
