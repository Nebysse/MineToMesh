package com.onecuber.mcgltf.gltf;

import com.onecuber.mcgltf.scene.Vertex;
import java.util.List;

public record AccessorBounds(float[] min, float[] max) {
    public AccessorBounds {
        min = min.clone();
        max = max.clone();
        if (min.length == 0 || min.length != max.length) {
            throw new IllegalArgumentException("Bounds must have matching non-empty dimensions");
        }
    }

    public static AccessorBounds positions(List<Vertex> vertices) {
        if (vertices.isEmpty()) {
            throw new IllegalArgumentException("Position bounds require at least one vertex");
        }
        float[] values = new float[Math.multiplyExact(vertices.size(), 3)];
        int cursor = 0;
        for (Vertex vertex : vertices) {
            values[cursor++] = vertex.position().x();
            values[cursor++] = vertex.position().y();
            values[cursor++] = vertex.position().z();
        }
        return of(values, 3);
    }

    public static AccessorBounds of(float[] values, int componentCount) {
        if (componentCount <= 0 || values.length == 0 || values.length % componentCount != 0) {
            throw new IllegalArgumentException("Values must contain complete non-empty elements");
        }
        float[] min = new float[componentCount];
        float[] max = new float[componentCount];
        java.util.Arrays.fill(min, Float.POSITIVE_INFINITY);
        java.util.Arrays.fill(max, Float.NEGATIVE_INFINITY);
        for (int index = 0; index < values.length; index++) {
            float value = values[index];
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Accessor values must be finite");
            }
            int component = index % componentCount;
            min[component] = Math.min(min[component], value);
            max[component] = Math.max(max[component], value);
        }
        return new AccessorBounds(min, max);
    }

    @Override
    public float[] min() {
        return min.clone();
    }

    @Override
    public float[] max() {
        return max.clone();
    }
}
