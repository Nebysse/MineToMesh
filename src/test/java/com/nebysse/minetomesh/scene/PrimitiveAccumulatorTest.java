package com.nebysse.minetomesh.scene;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PrimitiveAccumulatorTest {
    @Test
    void mergesMatchingStreamsAndOffsetsTheirIndices() {
        MaterialKey material = material("stone");
        PrimitiveAccumulator accumulator = new PrimitiveAccumulator("chunk/0/0/0");
        accumulator.append(material, PrimitiveMode.TRIANGLES, vertices(3));
        accumulator.append(material, PrimitiveMode.TRIANGLES, vertices(3));

        PrimitiveAccumulator.SealResult result = accumulator.seal();

        assertEquals(1, result.primitives().size());
        assertEquals(6, result.primitives().getFirst().vertices().size());
        assertArrayEquals(new int[] {0, 2, 1, 3, 5, 4}, result.primitives().getFirst().indices());
        assertEquals(0, result.diagnostics().size());
    }

    @Test
    void preservesFirstUseOrderForDifferentGroups() {
        PrimitiveAccumulator accumulator = new PrimitiveAccumulator("object");
        MaterialKey stone = material("stone");
        MaterialKey glass = material("glass");
        accumulator.append(stone, PrimitiveMode.TRIANGLES, vertices(3));
        accumulator.append(glass, PrimitiveMode.LINES, vertices(2));

        List<PrimitiveData> primitives = accumulator.seal().primitives();

        assertEquals(stone, primitives.get(0).material());
        assertEquals(glass, primitives.get(1).material());
    }

    @Test
    void primitiveDataDefensivelyCopiesStreamCountsAndValidatesCoverage() {
        int[] streamCounts = {3};
        PrimitiveData primitive = new PrimitiveData(
                vertices(3), PrimitiveMode.TRIANGLES, streamCounts, material("stone"));
        streamCounts[0] = 99;

        assertArrayEquals(new int[] {3}, primitive.streamVertexCounts());
        int[] returned = primitive.streamVertexCounts();
        returned[0] = 99;
        assertArrayEquals(new int[] {3}, primitive.streamVertexCounts());
        assertThrows(IllegalArgumentException.class,
                () -> new PrimitiveData(
                        vertices(3), PrimitiveMode.LINES, new int[] {2}, material("stone")));
    }

    @Test
    void overlayNodesDefensivelyCopyTheirPrimitiveList() {
        PrimitiveData primitive = new PrimitiveData(
                vertices(3), PrimitiveMode.TRIANGLES, new int[] {3}, material("overlay"));
        CapturedNode overlay = new CapturedNode(
                "selection/grass_side_overlay",
                CapturedNode.Kind.OVERLAY,
                List.of(primitive),
                Map.of("scope", "selection"));

        assertThrows(UnsupportedOperationException.class,
                () -> overlay.primitives().add(primitive));
    }

    @Test
    void sceneBatchesAreImmutableAndCountersAddExactly() {
        PrimitiveData primitive = new PrimitiveData(
                vertices(3), PrimitiveMode.TRIANGLES, new int[] {3}, material("stone"));
        CapturedNode node = new CapturedNode("node", CapturedNode.Kind.CHUNK, List.of(primitive), Map.of("x", 1));
        ChunkBatch batch = new ChunkBatch(List.of(node), List.of(), BatchCounters.ZERO);

        assertThrows(UnsupportedOperationException.class, () -> batch.nodes().add(node));
        BatchCounters sum = new BatchCounters(1, 2, 3, 4, 5, 6, 7, 8, 9)
                .plus(new BatchCounters(9, 8, 7, 6, 5, 4, 3, 2, 1));
        assertEquals(new BatchCounters(10, 10, 10, 10, 10, 10, 10, 10, 10), sum);
    }

    private static MaterialKey material(String name) {
        return new MaterialKey(
                new TextureKey(TextureKey.Kind.RESOURCE, "minecraft:block/" + name,
                        "textures/minecraft/block/" + name + ".png"),
                MaterialKey.AlphaMode.OPAQUE,
                Optional.empty(),
                false,
                false,
                MaterialKey.BlendSemantic.STANDARD,
                MaterialKey.SamplerMode.NEAREST);
    }

    private static List<Vertex> vertices(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new Vertex(new Vec3f(i, 0.0F, 0.0F), Vec3f.UP,
                        new Vec2f(0.0F, 0.0F), ColorRgba.WHITE))
                .toList();
    }
}
