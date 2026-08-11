package com.nebysse.minetomesh.obj;

import com.nebysse.minetomesh.scene.PrimitiveData;
import com.nebysse.minetomesh.scene.PrimitiveMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ObjTopologyConverter {
    private ObjTopologyConverter() {
    }

    public static List<int[]> faces(PrimitiveData primitive) {
        Objects.requireNonNull(primitive, "primitive");
        List<int[]> faces = new ArrayList<>();
        int offset = 0;
        for (int count : primitive.streamVertexCounts()) {
            appendFaces(faces, primitive.sourceMode(), offset, count);
            offset = Math.addExact(offset, count);
        }
        return immutableElements(faces);
    }

    public static List<int[]> lines(PrimitiveData primitive) {
        Objects.requireNonNull(primitive, "primitive");
        List<int[]> lines = new ArrayList<>();
        int offset = 0;
        for (int count : primitive.streamVertexCounts()) {
            appendLines(lines, primitive.sourceMode(), offset, count);
            offset = Math.addExact(offset, count);
        }
        return immutableElements(lines);
    }

    private static void appendFaces(
            List<int[]> output,
            PrimitiveMode mode,
            int offset,
            int count) {
        switch (mode) {
            case QUADS -> {
                int complete = count - count % 4;
                for (int index = 0; index < complete; index += 4) {
                    output.add(new int[] {
                            offset + index,
                            offset + index + 3,
                            offset + index + 2,
                            offset + index + 1});
                }
            }
            case TRIANGLES -> {
                int complete = count - count % 3;
                for (int index = 0; index < complete; index += 3) {
                    output.add(new int[] {
                            offset + index,
                            offset + index + 2,
                            offset + index + 1});
                }
            }
            case TRIANGLE_FAN -> {
                for (int index = 1; index < count - 1; index++) {
                    output.add(new int[] {
                            offset,
                            offset + index + 1,
                            offset + index});
                }
            }
            case TRIANGLE_STRIP -> {
                for (int index = 0; index < count - 2; index++) {
                    if ((index & 1) == 0) {
                        output.add(new int[] {
                                offset + index,
                                offset + index + 2,
                                offset + index + 1});
                    } else {
                        output.add(new int[] {
                                offset + index + 1,
                                offset + index + 2,
                                offset + index});
                    }
                }
            }
            case LINES, LINE_STRIP -> {
                // Line topology is emitted by lines().
            }
        }
    }

    private static void appendLines(
            List<int[]> output,
            PrimitiveMode mode,
            int offset,
            int count) {
        switch (mode) {
            case LINES -> {
                int complete = count - count % 2;
                for (int index = 0; index < complete; index += 2) {
                    output.add(new int[] {offset + index, offset + index + 1});
                }
            }
            case LINE_STRIP -> {
                for (int index = 0; index < count - 1; index++) {
                    output.add(new int[] {offset + index, offset + index + 1});
                }
            }
            case QUADS, TRIANGLES, TRIANGLE_FAN, TRIANGLE_STRIP -> {
                // Face topology is emitted by faces().
            }
        }
    }

    private static List<int[]> immutableElements(List<int[]> values) {
        return values.stream().map(int[]::clone).toList();
    }
}
