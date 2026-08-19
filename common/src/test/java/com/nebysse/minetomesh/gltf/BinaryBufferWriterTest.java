package com.nebysse.minetomesh.gltf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nebysse.minetomesh.scene.ColorRgba;
import com.nebysse.minetomesh.scene.Vec2f;
import com.nebysse.minetomesh.scene.Vec3f;
import com.nebysse.minetomesh.scene.Vertex;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import org.junit.jupiter.api.Test;

class BinaryBufferWriterTest {
    @Test
    void writesAlignedLittleEndianSegments() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        List<Vertex> vertices = List.of(
                vertex(0.0F, 0.0F, -1.0F, 1, 2, 3, 4),
                vertex(2.0F, 3.0F, 4.0F, 5, 6, 7, 8),
                vertex(1.0F, 1.0F, 1.0F, 9, 10, 11, 12));

        try (BinaryBufferWriter writer = new BinaryBufferWriter(bytes)) {
            BinaryBufferWriter.Segment colors = writer.writeColors(vertices);
            BinaryBufferWriter.Segment positions = writer.writePositions(vertices);
            BinaryBufferWriter.Segment indices = writer.writeIndices(new int[] {0, 1, 2});

            assertEquals(12L, colors.byteLength());
            assertEquals(12L, positions.byteOffset());
            assertEquals(0L, colors.byteOffset() % 4L);
            assertEquals(0L, positions.byteOffset() % 4L);
            assertEquals(0L, indices.byteOffset() % 4L);
            assertEquals(60L, writer.byteLength());
        }

        byte[] raw = bytes.toByteArray();
        assertEquals(1, raw[0] & 0xFF);
        assertEquals(4, raw[3] & 0xFF);
        assertEquals(0.0F, ByteBuffer.wrap(raw, 12, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat());
        assertEquals(2, ByteBuffer.wrap(raw, 56, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
    }

    @Test
    void alignsAfterUnpaddedColorPayload() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BinaryBufferWriter writer = new BinaryBufferWriter(bytes)) {
            BinaryBufferWriter.Segment colors = writer.writeColors(List.of(vertex(0, 0, 0, 1, 2, 3, 4)));
            BinaryBufferWriter.Segment indices = writer.writeIndices(new int[] {0});
            assertEquals(4L, colors.byteLength());
            assertEquals(4L, indices.byteOffset());
        }
    }

    @Test
    void rejectsInvalidIndicesBeforeWritingBytes() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BinaryBufferWriter writer = new BinaryBufferWriter(bytes)) {
            assertThrows(IllegalArgumentException.class, () -> writer.writeIndices(new int[] {-1}));
            assertEquals(0L, writer.byteLength());
            assertTrue(bytes.size() == 0);
        }
    }

    private static Vertex vertex(float x, float y, float z, int r, int g, int b, int a) {
        return new Vertex(new Vec3f(x, y, z), new Vec3f(0.0F, 1.0F, 0.0F),
                new Vec2f(0.25F, 0.75F), new ColorRgba(r, g, b, a));
    }
}
