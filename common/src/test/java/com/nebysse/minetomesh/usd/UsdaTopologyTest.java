package com.nebysse.minetomesh.usd;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nebysse.minetomesh.scene.PrimitiveMode;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class UsdaTopologyTest {
    @Test
    void preservesQuadAndExpandsStripWinding() {
        UsdaTopology.Surface quad = UsdaTopology.surface(
                PrimitiveMode.QUADS, new int[] {4, 8}, "quad");
        assertArrayEquals(new int[] {4, 4, 4}, quad.faceVertexCounts());
        assertArrayEquals(IntStream.range(0, 12).toArray(), quad.faceVertexIndices());

        UsdaTopology.Surface strip = UsdaTopology.surface(
                PrimitiveMode.TRIANGLE_STRIP, new int[] {4}, "strip");
        assertArrayEquals(new int[] {3, 3}, strip.faceVertexCounts());
        assertArrayEquals(new int[] {0, 1, 2, 2, 1, 3}, strip.faceVertexIndices());
    }

    @Test
    void convertsLineStreamsToCurves() {
        assertArrayEquals(new int[] {2, 2}, UsdaTopology.curves(
                PrimitiveMode.LINES, new int[] {4}, "lines").curveVertexCounts());
        assertArrayEquals(new int[] {4, 3}, UsdaTopology.curves(
                PrimitiveMode.LINE_STRIP, new int[] {4, 3}, "strip").curveVertexCounts());
    }

    @Test
    void escapesNamesAndTextDeterministically() {
        assertEquals("chunk_1", UsdaNames.identifier("区段/chunk 1"));
        assertEquals("unnamed", UsdaNames.identifier("空白"));
        assertEquals("_12rail", UsdaNames.identifier("12rail"));
        assertEquals("\"a\\\"b\\\\c\"", UsdaText.quoted("a\"b\\c"));
    }
}
