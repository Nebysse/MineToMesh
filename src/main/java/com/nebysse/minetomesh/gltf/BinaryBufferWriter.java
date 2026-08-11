package com.nebysse.minetomesh.gltf;

import com.nebysse.minetomesh.scene.Vertex;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class BinaryBufferWriter implements Closeable {
    private final OutputStream output;
    private long byteLength;
    private boolean closed;

    public BinaryBufferWriter(OutputStream output) {
        this.output = Objects.requireNonNull(output, "output");
    }

    public Segment writePositions(List<Vertex> vertices) throws IOException {
        List<Vertex> values = validateVertices(vertices);
        AccessorBounds bounds = AccessorBounds.positions(values);
        long offset = beginSegment();
        for (Vertex vertex : values) {
            writeFloat(vertex.position().x());
            writeFloat(vertex.position().y());
            writeFloat(vertex.position().z());
        }
        return segment(offset, GltfConstants.ARRAY_BUFFER, GltfConstants.FLOAT, 3, values.size(), bounds);
    }

    public Segment writeNormals(List<Vertex> vertices) throws IOException {
        List<Vertex> values = validateVertices(vertices);
        long offset = beginSegment();
        for (Vertex vertex : values) {
            writeFloat(vertex.normal().x());
            writeFloat(vertex.normal().y());
            writeFloat(vertex.normal().z());
        }
        return segment(offset, GltfConstants.ARRAY_BUFFER, GltfConstants.FLOAT, 3, values.size(), null);
    }

    public Segment writeTexCoords(List<Vertex> vertices) throws IOException {
        List<Vertex> values = validateVertices(vertices);
        long offset = beginSegment();
        for (Vertex vertex : values) {
            writeFloat(vertex.uv().x());
            writeFloat(vertex.uv().y());
        }
        return segment(offset, GltfConstants.ARRAY_BUFFER, GltfConstants.FLOAT, 2, values.size(), null);
    }

    public Segment writeColors(List<Vertex> vertices) throws IOException {
        List<Vertex> values = validateVertices(vertices);
        long offset = beginSegment();
        for (Vertex vertex : values) {
            writeByte(vertex.color().red());
            writeByte(vertex.color().green());
            writeByte(vertex.color().blue());
            writeByte(vertex.color().alpha());
        }
        return segment(offset, GltfConstants.ARRAY_BUFFER, GltfConstants.UNSIGNED_BYTE, 4, values.size(), null);
    }

    public Segment writeIndices(int[] indices) throws IOException {
        Objects.requireNonNull(indices, "indices");
        if (indices.length == 0) {
            throw new IllegalArgumentException("Index segment must not be empty");
        }
        for (int index : indices) {
            if (index < 0) {
                throw new IllegalArgumentException("Indices must be unsigned");
            }
        }
        long offset = beginSegment();
        for (int index : indices) {
            writeInt(index);
        }
        return segment(offset, GltfConstants.ELEMENT_ARRAY_BUFFER, GltfConstants.UNSIGNED_INT,
                1, indices.length, null);
    }

    public long byteLength() {
        return byteLength;
    }

    private List<Vertex> validateVertices(List<Vertex> vertices) {
        requireOpen();
        List<Vertex> values = List.copyOf(vertices);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Vertex segment must not be empty");
        }
        for (Vertex vertex : values) {
            requireFinite(vertex.position().x());
            requireFinite(vertex.position().y());
            requireFinite(vertex.position().z());
            requireFinite(vertex.normal().x());
            requireFinite(vertex.normal().y());
            requireFinite(vertex.normal().z());
            requireFinite(vertex.uv().x());
            requireFinite(vertex.uv().y());
        }
        return values;
    }

    private static void requireFinite(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Vertex attributes must be finite");
        }
    }

    private long beginSegment() throws IOException {
        requireOpen();
        while ((byteLength & 3L) != 0L) {
            writeByte(0);
        }
        return byteLength;
    }

    private Segment segment(
            long offset,
            int target,
            int componentType,
            int componentCount,
            int elementCount,
            AccessorBounds bounds) {
        return new Segment(
                offset,
                byteLength - offset,
                target,
                componentType,
                componentCount,
                elementCount,
                bounds == null ? Optional.empty() : Optional.of(bounds.min()),
                bounds == null ? Optional.empty() : Optional.of(bounds.max()));
    }

    private void writeFloat(float value) throws IOException {
        writeInt(Float.floatToRawIntBits(value));
    }

    private void writeInt(int value) throws IOException {
        writeByte(value);
        writeByte(value >>> 8);
        writeByte(value >>> 16);
        writeByte(value >>> 24);
    }

    private void writeByte(int value) throws IOException {
        output.write(value & 0xFF);
        byteLength = Math.addExact(byteLength, 1L);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Binary buffer writer is closed");
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            output.close();
        }
    }

    public record Segment(
            long byteOffset,
            long byteLength,
            int target,
            int componentType,
            int componentCount,
            int elementCount,
            Optional<float[]> min,
            Optional<float[]> max) {
        public Segment {
            min = copy(min);
            max = copy(max);
        }

        @Override
        public Optional<float[]> min() {
            return copy(min);
        }

        @Override
        public Optional<float[]> max() {
            return copy(max);
        }

        private static Optional<float[]> copy(Optional<float[]> value) {
            Objects.requireNonNull(value, "value");
            return value.map(float[]::clone);
        }
    }
}
