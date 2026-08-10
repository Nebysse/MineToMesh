package com.onecuber.mcgltf.scene;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TopologyConverter {
    private TopologyConverter() {
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
