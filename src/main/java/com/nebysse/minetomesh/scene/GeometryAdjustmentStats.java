package com.nebysse.minetomesh.scene;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record GeometryAdjustmentStats(
        long coplanarGroups,
        long offsetFaces,
        int maxLayers,
        Map<String, Long> byBlock) {
    public static final GeometryAdjustmentStats ZERO =
            new GeometryAdjustmentStats(0, 0, 0, Map.of());

    public GeometryAdjustmentStats {
        if (coplanarGroups < 0 || offsetFaces < 0 || maxLayers < 0) {
            throw new IllegalArgumentException("Geometry adjustment counts must not be negative");
        }
        Objects.requireNonNull(byBlock, "byBlock");
        TreeMap<String, Long> sorted = new TreeMap<>();
        byBlock.forEach((key, value) -> {
            Objects.requireNonNull(key, "block id");
            Objects.requireNonNull(value, "block group count");
            if (value < 0) {
                throw new IllegalArgumentException("Block group count must not be negative");
            }
            sorted.put(key, value);
        });
        byBlock = Collections.unmodifiableMap(sorted);
    }

    public static GeometryAdjustmentStats forBlock(
            String blockId, long groups, long faces, int layers) {
        Objects.requireNonNull(blockId, "blockId");
        return groups == 0
                ? ZERO
                : new GeometryAdjustmentStats(groups, faces, layers, Map.of(blockId, groups));
    }

    public GeometryAdjustmentStats plus(GeometryAdjustmentStats other) {
        Objects.requireNonNull(other, "other");
        TreeMap<String, Long> blocks = new TreeMap<>(byBlock);
        other.byBlock.forEach((key, value) -> blocks.merge(key, value, Math::addExact));
        return new GeometryAdjustmentStats(
                Math.addExact(coplanarGroups, other.coplanarGroups),
                Math.addExact(offsetFaces, other.offsetFaces),
                Math.max(maxLayers, other.maxLayers),
                blocks);
    }
}
