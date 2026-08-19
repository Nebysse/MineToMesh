package com.nebysse.minetomesh.usd;

import com.nebysse.minetomesh.scene.PrimitiveMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class UsdaTopology {
    private UsdaTopology() {
    }

    public static Surface surface(PrimitiveMode mode, int[] counts, String objectId) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(counts, "counts");
        Objects.requireNonNull(objectId, "objectId");
        List<Integer> faceCounts = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        int offset = 0;
        for (int count : counts) {
            switch (mode) {
                case QUADS -> grouped(faceCounts, indices, offset, count, 4);
                case TRIANGLES -> grouped(faceCounts, indices, offset, count, 3);
                case TRIANGLE_FAN -> {
                    for (int corner = 1; corner < count - 1; corner++) {
                        faceCounts.add(3);
                        indices.add(offset);
                        indices.add(offset + corner);
                        indices.add(offset + corner + 1);
                    }
                }
                case TRIANGLE_STRIP -> {
                    for (int corner = 0; corner < count - 2; corner++) {
                        faceCounts.add(3);
                        if ((corner & 1) == 0) {
                            indices.add(offset + corner);
                            indices.add(offset + corner + 1);
                        } else {
                            indices.add(offset + corner + 1);
                            indices.add(offset + corner);
                        }
                        indices.add(offset + corner + 2);
                    }
                }
                case LINES, LINE_STRIP -> throw new IllegalArgumentException(
                        "Line topology cannot be written as a USD surface: " + objectId);
            }
            offset = Math.addExact(offset, count);
        }
        return new Surface(toArray(faceCounts), toArray(indices));
    }

    public static Curves curves(PrimitiveMode mode, int[] counts, String objectId) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(counts, "counts");
        List<Integer> curveCounts = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        int offset = 0;
        for (int count : counts) {
            switch (mode) {
                case LINES -> {
                    int complete = count - count % 2;
                    for (int base = 0; base < complete; base += 2) {
                        curveCounts.add(2);
                        indices.add(offset + base);
                        indices.add(offset + base + 1);
                    }
                }
                case LINE_STRIP -> {
                    if (count >= 2) {
                        curveCounts.add(count);
                        for (int vertex = 0; vertex < count; vertex++) {
                            indices.add(offset + vertex);
                        }
                    }
                }
                case QUADS, TRIANGLES, TRIANGLE_FAN, TRIANGLE_STRIP ->
                        throw new IllegalArgumentException(
                                "Surface topology cannot be written as USD curves: " + objectId);
            }
            offset = Math.addExact(offset, count);
        }
        return new Curves(toArray(curveCounts), toArray(indices));
    }

    private static void grouped(List<Integer> counts, List<Integer> indices,
            int offset, int count, int size) {
        int complete = count - count % size;
        for (int base = 0; base < complete; base += size) {
            counts.add(size);
            for (int corner = 0; corner < size; corner++) {
                indices.add(offset + base + corner);
            }
        }
    }

    private static int[] toArray(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    public record Surface(int[] faceVertexCounts, int[] faceVertexIndices) {
        public Surface {
            faceVertexCounts = faceVertexCounts.clone();
            faceVertexIndices = faceVertexIndices.clone();
        }

        @Override
        public int[] faceVertexCounts() {
            return faceVertexCounts.clone();
        }

        @Override
        public int[] faceVertexIndices() {
            return faceVertexIndices.clone();
        }
    }

    public record Curves(int[] curveVertexCounts, int[] vertexIndices) {
        public Curves {
            curveVertexCounts = curveVertexCounts.clone();
            vertexIndices = vertexIndices.clone();
        }

        @Override
        public int[] curveVertexCounts() {
            return curveVertexCounts.clone();
        }

        @Override
        public int[] vertexIndices() {
            return vertexIndices.clone();
        }
    }
}
