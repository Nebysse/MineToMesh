package com.nebysse.minetomesh.scene;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TopologyConverter {
    private TopologyConverter() {
    }

    public static ConvertedTopology convert(PrimitiveData primitive, String objectId) {
        Objects.requireNonNull(primitive, "primitive");
        Objects.requireNonNull(objectId, "objectId");
        List<Integer> indices = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        int offset = 0;
        for (int count : primitive.streamVertexCounts()) {
            ConvertedTopology stream = convertExplicit(
                    primitive.sourceMode(), count, objectId);
            for (int index : stream.indices()) {
                indices.add(Math.addExact(offset, index));
            }
            diagnostics.addAll(stream.diagnostics());
            offset = Math.addExact(offset, count);
        }
        return new ConvertedTopology(
                gltfMode(primitive.sourceMode()),
                indices.stream().mapToInt(Integer::intValue).toArray(),
                diagnostics);
    }

    public static ConvertedTopology convert(PrimitiveMode mode, int vertexCount, String objectId) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(objectId, "objectId");
        if (vertexCount < 0) {
            throw new IllegalArgumentException("Vertex count must not be negative");
        }
        return switch (mode) {
            case QUADS -> groupedTriangles(vertexCount, 4, objectId, true);
            case TRIANGLES -> groupedTriangles(vertexCount, 3, objectId, false);
            case TRIANGLE_FAN -> fan(vertexCount, objectId);
            case TRIANGLE_STRIP -> strip(vertexCount, objectId);
            case LINES -> lines(vertexCount, objectId);
            case LINE_STRIP -> lineStrip(vertexCount, objectId);
        };
    }

    private static ConvertedTopology convertExplicit(
            PrimitiveMode mode, int count, String objectId) {
        return switch (mode) {
            case QUADS -> groupedTriangles(count, 4, objectId, true);
            case TRIANGLES -> groupedTriangles(count, 3, objectId, false);
            case TRIANGLE_FAN -> explicitFan(count, objectId);
            case TRIANGLE_STRIP -> explicitStrip(count, objectId);
            case LINES -> lines(count, objectId);
            case LINE_STRIP -> explicitLineStrip(count, objectId);
        };
    }

    private static int gltfMode(PrimitiveMode mode) {
        return switch (mode) {
            case QUADS, TRIANGLES, TRIANGLE_FAN, TRIANGLE_STRIP -> 4;
            case LINES, LINE_STRIP -> 1;
        };
    }

    private static ConvertedTopology groupedTriangles(
            int count, int groupSize, String objectId, boolean quads) {
        int complete = count - count % groupSize;
        int triangleCount = quads ? complete / 4 * 2 : complete / 3;
        int[] indices = new int[triangleCount * 3];
        int cursor = 0;
        for (int base = 0; base < complete; base += groupSize) {
            if (quads) {
                indices[cursor++] = base;
                indices[cursor++] = base + 2;
                indices[cursor++] = base + 1;
                indices[cursor++] = base;
                indices[cursor++] = base + 3;
                indices[cursor++] = base + 2;
            } else {
                indices[cursor++] = base;
                indices[cursor++] = base + 2;
                indices[cursor++] = base + 1;
            }
        }
        return new ConvertedTopology(4, indices, incompleteDiagnostic(count - complete, objectId));
    }

    private static ConvertedTopology explicitFan(int count, String objectId) {
        if (count < 3) {
            return new ConvertedTopology(4, new int[0], incompleteDiagnostic(count, objectId));
        }
        int[] indices = new int[(count - 2) * 3];
        int cursor = 0;
        for (int index = 1; index < count - 1; index++) {
            indices[cursor++] = 0;
            indices[cursor++] = index + 1;
            indices[cursor++] = index;
        }
        return new ConvertedTopology(4, indices, List.of());
    }

    private static ConvertedTopology explicitStrip(int count, String objectId) {
        if (count < 3) {
            return new ConvertedTopology(4, new int[0], incompleteDiagnostic(count, objectId));
        }
        int[] indices = new int[(count - 2) * 3];
        int cursor = 0;
        for (int index = 0; index < count - 2; index++) {
            if ((index & 1) == 0) {
                indices[cursor++] = index;
                indices[cursor++] = index + 2;
                indices[cursor++] = index + 1;
            } else {
                indices[cursor++] = index + 1;
                indices[cursor++] = index + 2;
                indices[cursor++] = index;
            }
        }
        return new ConvertedTopology(4, indices, List.of());
    }

    private static ConvertedTopology explicitLineStrip(int count, String objectId) {
        if (count < 2) {
            return new ConvertedTopology(1, new int[0], incompleteDiagnostic(count, objectId));
        }
        int[] indices = new int[(count - 1) * 2];
        int cursor = 0;
        for (int index = 0; index < count - 1; index++) {
            indices[cursor++] = index;
            indices[cursor++] = index + 1;
        }
        return new ConvertedTopology(1, indices, List.of());
    }

    private static ConvertedTopology fan(int count, String objectId) {
        if (count < 3) {
            return new ConvertedTopology(6, new int[0], incompleteDiagnostic(count, objectId));
        }
        int[] indices = new int[count];
        indices[0] = 0;
        for (int i = 1; i < count; i++) {
            indices[i] = count - i;
        }
        return new ConvertedTopology(6, indices, List.of());
    }

    private static ConvertedTopology strip(int count, String objectId) {
        if (count < 3) {
            return new ConvertedTopology(5, new int[0], incompleteDiagnostic(count, objectId));
        }
        boolean needsParityDuplicate = count % 2 == 0;
        int[] indices = new int[count + (needsParityDuplicate ? 1 : 0)];
        int cursor = 0;
        if (needsParityDuplicate) {
            indices[cursor++] = count - 1;
        }
        for (int i = count - 1; i >= 0; i--) {
            indices[cursor++] = i;
        }
        return new ConvertedTopology(5, indices, List.of());
    }

    private static ConvertedTopology lines(int count, String objectId) {
        int complete = count - count % 2;
        return new ConvertedTopology(1, range(complete), incompleteDiagnostic(count - complete, objectId));
    }

    private static ConvertedTopology lineStrip(int count, String objectId) {
        if (count < 2) {
            return new ConvertedTopology(3, new int[0], incompleteDiagnostic(count, objectId));
        }
        return new ConvertedTopology(3, range(count), List.of());
    }

    private static int[] range(int count) {
        int[] values = new int[count];
        Arrays.setAll(values, index -> index);
        return values;
    }

    private static List<Diagnostic> incompleteDiagnostic(int discarded, String objectId) {
        if (discarded == 0) {
            return List.of();
        }
        return List.of(new Diagnostic(
                Diagnostic.Severity.WARNING,
                "INCOMPLETE_PRIMITIVE",
                objectId,
                Optional.empty(),
                "",
                "",
                "Discarded " + discarded + " incomplete primitive vertices"));
    }

    public record ConvertedTopology(int gltfMode, int[] indices, List<Diagnostic> diagnostics) {
        public ConvertedTopology {
            indices = indices.clone();
            diagnostics = List.copyOf(diagnostics);
        }

        @Override
        public int[] indices() {
            return indices.clone();
        }
    }
}
