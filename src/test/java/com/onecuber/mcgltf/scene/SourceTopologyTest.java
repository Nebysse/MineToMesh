package com.onecuber.mcgltf.scene;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SourceTopologyTest {
    @Test
    void accumulatorRetainsQuadModeAndIndividualStreamLengths() {
        PrimitiveAccumulator accumulator = new PrimitiveAccumulator("chunk/0/0/0");
        accumulator.append(material(), PrimitiveMode.QUADS, vertices(4));
        accumulator.append(material(), PrimitiveMode.QUADS, vertices(8));

        PrimitiveData primitive = accumulator.seal().primitives().getFirst();

        assertEquals(PrimitiveMode.QUADS, primitive.sourceMode());
        assertArrayEquals(new int[] {4, 8}, primitive.streamVertexCounts());
        assertEquals(12, primitive.vertices().size());
    }

    @Test
    void gltfConversionDoesNotConnectSeparateTriangleStrips() {
        PrimitiveData primitive = new PrimitiveData(
                vertices(8), PrimitiveMode.TRIANGLE_STRIP,
                new int[] {4, 4}, material());

        TopologyConverter.ConvertedTopology converted =
                TopologyConverter.convert(primitive, "object");

        assertEquals(4, converted.gltfMode());
        assertArrayEquals(new int[] {
                0, 2, 1, 2, 3, 1,
                4, 6, 5, 6, 7, 5
        }, converted.indices());
    }

    @Test
    void primitiveRejectsStreamLengthsThatDoNotCoverVertices() {
        assertThrows(IllegalArgumentException.class, () -> new PrimitiveData(
                vertices(4), PrimitiveMode.QUADS, new int[] {3}, material()));
    }

    private static MaterialKey material() {
        return new MaterialKey(
                new TextureKey(TextureKey.Kind.RESOURCE, "minecraft:block/stone",
                        "textures/minecraft/block/stone.png"),
                MaterialKey.AlphaMode.OPAQUE,
                Optional.empty(),
                false,
                false,
                MaterialKey.BlendSemantic.STANDARD,
                MaterialKey.SamplerMode.NEAREST);
    }

    private static List<Vertex> vertices(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new Vertex(
                        new Vec3f(index, 0.0F, 0.0F),
                        Vec3f.UP,
                        new Vec2f(0.0F, 0.0F),
                        ColorRgba.WHITE))
                .toList();
    }
}
