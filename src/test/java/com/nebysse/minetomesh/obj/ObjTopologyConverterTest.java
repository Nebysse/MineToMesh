package com.nebysse.minetomesh.obj;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nebysse.minetomesh.scene.ColorRgba;
import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.PrimitiveData;
import com.nebysse.minetomesh.scene.PrimitiveMode;
import com.nebysse.minetomesh.scene.TextureKey;
import com.nebysse.minetomesh.scene.Vec2f;
import com.nebysse.minetomesh.scene.Vec3f;
import com.nebysse.minetomesh.scene.Vertex;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ObjTopologyConverterTest {
    @Test
    void quadPreservesFourVertexSourceOrder() {
        PrimitiveData quad = new PrimitiveData(
                vertices(4), PrimitiveMode.QUADS, new int[] {4}, material());

        List<int[]> faces = ObjTopologyConverter.faces(quad);

        assertEquals(1, faces.size());
        assertArrayEquals(new int[] {0, 1, 2, 3}, faces.getFirst());
    }

    @Test
    void separateFansDoNotShareFaces() {
        PrimitiveData fans = new PrimitiveData(
                vertices(8), PrimitiveMode.TRIANGLE_FAN, new int[] {4, 4}, material());

        List<int[]> faces = ObjTopologyConverter.faces(fans);

        assertEquals(4, faces.size());
        assertArrayEquals(new int[] {0, 1, 2}, faces.get(0));
        assertArrayEquals(new int[] {0, 2, 3}, faces.get(1));
        assertArrayEquals(new int[] {4, 5, 6}, faces.get(2));
        assertArrayEquals(new int[] {4, 6, 7}, faces.get(3));
    }

    @Test
    void triangleStripPreservesSourceParity() {
        PrimitiveData strip = new PrimitiveData(
                vertices(4), PrimitiveMode.TRIANGLE_STRIP,
                new int[] {4}, material());

        List<int[]> faces = ObjTopologyConverter.faces(strip);

        assertEquals(2, faces.size());
        assertArrayEquals(new int[] {0, 1, 2}, faces.get(0));
        assertArrayEquals(new int[] {2, 1, 3}, faces.get(1));
    }

    @Test
    void lineStripBecomesIndependentLineElements() {
        PrimitiveData strip = new PrimitiveData(
                vertices(3), PrimitiveMode.LINE_STRIP, new int[] {3}, material());

        List<int[]> lines = ObjTopologyConverter.lines(strip);

        assertEquals(2, lines.size());
        assertArrayEquals(new int[] {0, 1}, lines.get(0));
        assertArrayEquals(new int[] {1, 2}, lines.get(1));
    }

    static MaterialKey material() {
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

    static List<Vertex> vertices(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new Vertex(
                        new Vec3f(index, index % 2, 0.0F),
                        Vec3f.UP,
                        new Vec2f(index / (float) Math.max(1, count - 1), 0.0F),
                        ColorRgba.WHITE))
                .toList();
    }
}
