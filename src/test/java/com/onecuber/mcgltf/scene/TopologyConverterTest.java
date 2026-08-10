package com.onecuber.mcgltf.scene;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TopologyConverterTest {
    @Test
    void mirrorsQuadAndTriangleWinding() {
        TopologyConverter.ConvertedTopology quad = TopologyConverter.convert(PrimitiveMode.QUADS, 4, "quad");
        TopologyConverter.ConvertedTopology triangle = TopologyConverter.convert(PrimitiveMode.TRIANGLES, 3, "triangle");

        assertEquals(4, quad.gltfMode());
        assertArrayEquals(new int[] {0, 2, 1, 0, 3, 2}, quad.indices());
        assertArrayEquals(new int[] {0, 2, 1}, triangle.indices());
    }

    @Test
    void mirrorsFanByReversingItsOuterVertices() {
        TopologyConverter.ConvertedTopology result = TopologyConverter.convert(
                PrimitiveMode.TRIANGLE_FAN, 4, "fan");

        assertEquals(6, result.gltfMode());
        assertArrayEquals(new int[] {0, 3, 2, 1}, result.indices());
    }

    @Test
    void mirrorsStripsWhilePreservingParity() {
        assertArrayEquals(new int[] {4, 3, 2, 1, 0},
                TopologyConverter.convert(PrimitiveMode.TRIANGLE_STRIP, 5, "odd-strip").indices());
        assertArrayEquals(new int[] {3, 3, 2, 1, 0},
                TopologyConverter.convert(PrimitiveMode.TRIANGLE_STRIP, 4, "even-strip").indices());
    }

    @Test
    void retainsLineOrder() {
        assertArrayEquals(new int[] {0, 1, 2, 3},
                TopologyConverter.convert(PrimitiveMode.LINES, 4, "lines").indices());
        assertArrayEquals(new int[] {0, 1, 2, 3},
                TopologyConverter.convert(PrimitiveMode.LINE_STRIP, 4, "line-strip").indices());
    }

    @Test
    void discardsIncompleteQuadAndTriangleTailsWithDiagnostics() {
        TopologyConverter.ConvertedTopology quads = TopologyConverter.convert(PrimitiveMode.QUADS, 6, "quads");
        TopologyConverter.ConvertedTopology triangles = TopologyConverter.convert(PrimitiveMode.TRIANGLES, 5, "triangles");

        assertArrayEquals(new int[] {0, 2, 1, 0, 3, 2}, quads.indices());
        assertArrayEquals(new int[] {0, 2, 1}, triangles.indices());
        assertEquals("INCOMPLETE_PRIMITIVE", quads.diagnostics().getFirst().code());
        assertEquals("INCOMPLETE_PRIMITIVE", triangles.diagnostics().getFirst().code());
        assertTrue(quads.diagnostics().getFirst().message().contains("2"));
    }
}
