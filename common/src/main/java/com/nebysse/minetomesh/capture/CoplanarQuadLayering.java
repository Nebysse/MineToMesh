package com.nebysse.minetomesh.capture;

import com.nebysse.minetomesh.scene.Vec3f;
import com.nebysse.minetomesh.scene.Vertex;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CoplanarQuadLayering {
    public static final float LAYER_OFFSET = 1.0F / 1024.0F;

    private CoplanarQuadLayering() {
    }

    public static Result apply(List<List<Vertex>> quads) {
        Objects.requireNonNull(quads, "quads");
        List<List<Vertex>> output = new ArrayList<>(quads.size());
        Map<FaceGeometryKey, List<Integer>> groups = new LinkedHashMap<>();
        for (int index = 0; index < quads.size(); index++) {
            List<Vertex> quad = List.copyOf(quads.get(index));
            if (quad.size() != 4) {
                throw new IllegalArgumentException("Coplanar layering requires four vertices");
            }
            output.add(quad);
            groups.computeIfAbsent(FaceGeometryKey.of(quad), ignored -> new ArrayList<>())
                    .add(index);
        }

        long groupCount = 0;
        long offsetFaces = 0;
        long invalidNormals = 0;
        int maxLayers = 0;
        for (List<Integer> indices : groups.values()) {
            if (indices.size() < 2) {
                continue;
            }
            groupCount++;
            maxLayers = Math.max(maxLayers, indices.size());
            for (int layer = 1; layer < indices.size(); layer++) {
                int index = indices.get(layer);
                Optional<Vec3f> normal = faceNormal(output.get(index));
                if (normal.isEmpty()) {
                    invalidNormals++;
                    continue;
                }
                float distance = layer * LAYER_OFFSET;
                Vec3f direction = normal.orElseThrow();
                output.set(index, output.get(index).stream().map(vertex -> {
                    Vec3f position = vertex.position();
                    return new Vertex(new Vec3f(
                            position.x() + direction.x() * distance,
                            position.y() + direction.y() * distance,
                            position.z() + direction.z() * distance),
                            vertex.normal(), vertex.uv(), vertex.color());
                }).toList());
                offsetFaces++;
            }
        }
        Statistics statistics = groupCount == 0
                ? Statistics.ZERO
                : new Statistics(groupCount, offsetFaces, maxLayers, invalidNormals);
        return new Result(output, statistics);
    }

    private static Optional<Vec3f> faceNormal(List<Vertex> quad) {
        float x = 0.0F;
        float y = 0.0F;
        float z = 0.0F;
        for (Vertex vertex : quad) {
            x += vertex.normal().x();
            y += vertex.normal().y();
            z += vertex.normal().z();
        }
        Optional<Vec3f> averaged = normalized(x, y, z);
        if (averaged.isPresent()) {
            return averaged;
        }
        Vec3f p0 = quad.get(0).position();
        Vec3f p1 = quad.get(1).position();
        Vec3f p2 = quad.get(2).position();
        float ax = p1.x() - p0.x();
        float ay = p1.y() - p0.y();
        float az = p1.z() - p0.z();
        float bx = p2.x() - p0.x();
        float by = p2.y() - p0.y();
        float bz = p2.z() - p0.z();
        return normalized(
                ay * bz - az * by,
                az * bx - ax * bz,
                ax * by - ay * bx);
    }

    private static Optional<Vec3f> normalized(float x, float y, float z) {
        double lengthSquared = (double) x * x + (double) y * y + (double) z * z;
        if (lengthSquared == 0.0D) {
            return Optional.empty();
        }
        float inverse = (float) (1.0D / Math.sqrt(lengthSquared));
        return Optional.of(new Vec3f(x * inverse, y * inverse, z * inverse));
    }

    private record PositionBits(int x, int y, int z) implements Comparable<PositionBits> {
        static PositionBits of(Vec3f value) {
            return new PositionBits(bits(value.x()), bits(value.y()), bits(value.z()));
        }

        private static int bits(float value) {
            return Float.floatToIntBits(value == 0.0F ? 0.0F : value);
        }

        @Override
        public int compareTo(PositionBits other) {
            int cx = Integer.compare(x, other.x);
            int cy = Integer.compare(y, other.y);
            return cx != 0 ? cx : cy != 0 ? cy : Integer.compare(z, other.z);
        }
    }

    private record FaceGeometryKey(List<PositionBits> positions) {
        static FaceGeometryKey of(List<Vertex> quad) {
            return new FaceGeometryKey(quad.stream()
                    .map(Vertex::position)
                    .map(PositionBits::of)
                    .sorted()
                    .toList());
        }
    }

    public record Statistics(
            long coplanarGroups,
            long offsetFaces,
            int maxLayers,
            long invalidNormals) {
        public static final Statistics ZERO = new Statistics(0, 0, 0, 0);
    }

    public record Result(List<List<Vertex>> quads, Statistics statistics) {
        public Result {
            quads = quads.stream().map(List::copyOf).toList();
            Objects.requireNonNull(statistics, "statistics");
        }
    }
}
