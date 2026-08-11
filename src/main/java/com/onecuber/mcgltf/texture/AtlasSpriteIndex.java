package com.onecuber.mcgltf.texture;

import com.onecuber.mcgltf.scene.Vec2f;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public final class AtlasSpriteIndex {
    static final int BUCKET_COUNT = 256;

    private static final Comparator<Region> RESOLUTION_ORDER =
            Comparator.comparingDouble(Region::area)
                    .thenComparing(region -> region.id().toString());

    private final Map<ResourceLocation, Region> byId;
    private final Map<Integer, List<Region>> buckets;

    public AtlasSpriteIndex(List<Region> regions) {
        Objects.requireNonNull(regions, "regions");
        Map<ResourceLocation, Region> indexedById = new LinkedHashMap<>();
        Map<Integer, List<Region>> indexedBuckets = new HashMap<>();
        for (Region region : regions) {
            Objects.requireNonNull(region, "region");
            if (indexedById.putIfAbsent(region.id(), region) != null) {
                throw new IllegalArgumentException("Duplicate atlas sprite region: " + region.id());
            }
            index(region, indexedBuckets);
        }
        byId = Map.copyOf(indexedById);
        Map<Integer, List<Region>> frozenBuckets = new HashMap<>();
        indexedBuckets.forEach((key, value) -> frozenBuckets.put(key, List.copyOf(value)));
        buckets = Map.copyOf(frozenBuckets);
    }

    public Resolution resolve(ResourceLocation declaredId, List<Vec2f> uvs) {
        Objects.requireNonNull(declaredId, "declaredId");
        Objects.requireNonNull(uvs, "uvs");
        if (uvs.isEmpty()) {
            throw new IllegalArgumentException("Atlas UV list must not be empty");
        }
        for (Vec2f uv : uvs) {
            Objects.requireNonNull(uv, "uv");
        }

        Region declared = byId.get(declaredId);
        if (declared != null && declared.contains(uvs)) {
            return new Resolution(Optional.of(declared), Kind.DECLARED);
        }

        Region resolved = candidatesAtCentroid(uvs).stream()
                .filter(region -> region.contains(uvs))
                .min(RESOLUTION_ORDER)
                .orElse(null);
        if (resolved != null) {
            return new Resolution(Optional.of(resolved), Kind.REDIRECTED);
        }
        return new Resolution(Optional.ofNullable(declared), Kind.FALLBACK);
    }

    private List<Region> candidatesAtCentroid(List<Vec2f> uvs) {
        float u = 0.0F;
        float v = 0.0F;
        for (Vec2f uv : uvs) {
            u += uv.x();
            v += uv.y();
        }
        u /= uvs.size();
        v /= uvs.size();
        return buckets.getOrDefault(bucketKey(bucket(u), bucket(v)), List.of());
    }

    private static void index(Region region, Map<Integer, List<Region>> indexedBuckets) {
        int minX = bucket(region.u0());
        int minY = bucket(region.v0());
        int maxX = bucket(Math.nextDown(region.u1()));
        int maxY = bucket(Math.nextDown(region.v1()));
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                indexedBuckets.computeIfAbsent(bucketKey(x, y), ignored -> new ArrayList<>())
                        .add(region);
            }
        }
    }

    private static int bucket(float coordinate) {
        if (!Float.isFinite(coordinate)) {
            throw new IllegalArgumentException("Atlas coordinate must be finite");
        }
        int value = (int) Math.floor(coordinate * BUCKET_COUNT);
        return Math.max(0, Math.min(BUCKET_COUNT - 1, value));
    }

    private static int bucketKey(int x, int y) {
        return y * BUCKET_COUNT + x;
    }

    public enum Kind {
        DECLARED,
        REDIRECTED,
        FALLBACK
    }

    public record Region(
            ResourceLocation id,
            float u0,
            float v0,
            float u1,
            float v1,
            int atlasWidth,
            int atlasHeight) {
        public Region {
            Objects.requireNonNull(id, "id");
            if (!Float.isFinite(u0) || !Float.isFinite(v0)
                    || !Float.isFinite(u1) || !Float.isFinite(v1)
                    || !(u0 < u1) || !(v0 < v1)
                    || atlasWidth <= 0 || atlasHeight <= 0) {
                throw new IllegalArgumentException("Invalid atlas sprite region");
            }
        }

        float area() {
            return (u1 - u0) * (v1 - v0);
        }

        boolean contains(List<Vec2f> uvs) {
            float epsilonU = 0.5F / atlasWidth;
            float epsilonV = 0.5F / atlasHeight;
            return uvs.stream().allMatch(uv ->
                    uv.x() >= u0 - epsilonU && uv.x() <= u1 + epsilonU
                            && uv.y() >= v0 - epsilonV && uv.y() <= v1 + epsilonV);
        }
    }

    public record Resolution(Optional<Region> region, Kind kind) {
        public Resolution {
            Objects.requireNonNull(region, "region");
            Objects.requireNonNull(kind, "kind");
        }
    }
}
