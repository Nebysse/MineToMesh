package com.nebysse.minetomesh.usd;

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

class StreamingUsdaSessionTest {
    @TempDir
    Path tempDir;

    @Test
    void writesQuadHierarchyMaterialAndFlippedUv() throws Exception {
        CapturedNode node = node("chunk/0/0/section/4", CapturedNode.Kind.CHUNK, quad(material()));
        StreamingUsdaSession.OutputStatistics stats;
        try (StreamingUsdaSession session = new StreamingUsdaSession(
                tempDir, "sample", Map.of("dimension", "minecraft:overworld"))) {
            session.append(new ChunkBatch(List.of(node), List.of(), BatchCounters.ZERO));
            stats = session.finish();
        }
        String usd = Files.readString(tempDir.resolve("sample.usda"));
        assertTrue(usd.startsWith("#usda 1.0"));
        assertTrue(usd.contains("upAxis = \"Y\""));
        assertTrue(usd.contains("metersPerUnit = 1"));
        assertTrue(usd.contains("def Xform \"Chunks\""));
        assertTrue(usd.contains("int[] faceVertexCounts = [4]"));
        assertTrue(usd.contains("uniform token subdivisionScheme = \"none\""));
        assertTrue(usd.contains("texCoord2f[] primvars:st"));
        assertTrue(usd.contains("prepend apiSchemas = [\"MaterialBindingAPI\"]"));
        assertTrue(usd.contains("rel material:binding"));
        assertEquals(1, stats.nodeCount());
        assertEquals(1, stats.primitiveCount());
        assertEquals(1, stats.faceCount());
        assertFalse(hasPartFiles());
    }

    @Test
    void globallyBatchesBlockEntitiesByMaterialAndOverlayBySelection() throws Exception {
        PrimitiveData triangle = triangle(material());
        CapturedNode first = node("create:belt/one", CapturedNode.Kind.BLOCK_ENTITY, triangle);
        CapturedNode second = node("create:belt/two", CapturedNode.Kind.BLOCK_ENTITY, triangle);
        CapturedNode overlay = node("selection/grass_side_overlay",
                CapturedNode.Kind.OVERLAY, quad(material()));
        try (StreamingUsdaSession session = new StreamingUsdaSession(tempDir, "batch", Map.of())) {
            session.append(new ChunkBatch(List.of(first, overlay), List.of(), BatchCounters.ZERO));
            session.append(new ChunkBatch(List.of(second, overlay), List.of(), BatchCounters.ZERO));
            session.finish();
        }
        String usd = Files.readString(tempDir.resolve("batch.usda"));
        assertEquals(1, occurrences(usd, "def Xform \"selection_grass_side_overlay\""));
        assertEquals(1, occurrences(usd, "def Mesh \"BlockEntities_"));
        assertEquals(1, occurrences(usd, "def Mesh \"Overlay_"));
        assertFalse(hasPartFiles());
    }

    @Test
    void closeWithoutFinishDeletesFragments() throws Exception {
        StreamingUsdaSession session = new StreamingUsdaSession(tempDir, "cancelled", Map.of());
        session.append(new ChunkBatch(List.of(
                node("entity", CapturedNode.Kind.ENTITY, triangle(material()))),
                List.of(), BatchCounters.ZERO));
        assertTrue(hasPartFiles());
        session.close();
        assertFalse(hasPartFiles());
        assertFalse(Files.exists(tempDir.resolve("cancelled.usda")));
    }

    static PrimitiveData quad(MaterialKey material) {
        return new PrimitiveData(List.of(
                vertex(0, 0), vertex(1, 0), vertex(1, 1), vertex(0, 1)),
                PrimitiveMode.QUADS, new int[] {4}, material);
    }

    static PrimitiveData triangle(MaterialKey material) {
        return new PrimitiveData(List.of(vertex(0, 0), vertex(1, 0), vertex(0, 1)),
                PrimitiveMode.TRIANGLES, new int[] {3}, material);
    }

    static MaterialKey material() {
        return new MaterialKey(
                new TextureKey(TextureKey.Kind.RESOURCE, "minecraft:block/stone",
                        "textures/minecraft/block/stone.png"),
                MaterialKey.AlphaMode.OPAQUE, Optional.empty(), false, false,
                MaterialKey.BlendSemantic.STANDARD, MaterialKey.SamplerMode.NEAREST);
    }

    private static CapturedNode node(String name, CapturedNode.Kind kind, PrimitiveData primitive) {
        return new CapturedNode(name, kind, List.of(primitive), Map.of());
    }

    private static Vertex vertex(float x, float y) {
        return new Vertex(new Vec3f(x, y, 0), Vec3f.UP,
                new Vec2f(x, y), new ColorRgba(255, 255, 255, 255));
    }

    private boolean hasPartFiles() throws Exception {
        try (var files = Files.list(tempDir)) {
            return files.anyMatch(path -> path.getFileName().toString().contains(".usdapart"));
        }
    }

    private static int occurrences(String text, String token) {
        return (text.length() - text.replace(token, "").length()) / token.length();
    }
}
