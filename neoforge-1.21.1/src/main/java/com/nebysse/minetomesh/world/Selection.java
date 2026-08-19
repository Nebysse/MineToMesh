package com.nebysse.minetomesh.world;

import java.util.Objects;

public record Selection(BlockPoint min, BlockPoint max) {
    public Selection {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        requireSameDimension(min, max);
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
            throw new IllegalArgumentException("Selection bounds must be normalized");
        }
    }

    public static Selection of(BlockPoint first, BlockPoint second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        requireSameDimension(first, second);
        String dimension = first.dimension();
        return new Selection(
                new BlockPoint(dimension,
                        Math.min(first.x(), second.x()),
                        Math.min(first.y(), second.y()),
                        Math.min(first.z(), second.z())),
                new BlockPoint(dimension,
                        Math.max(first.x(), second.x()),
                        Math.max(first.y(), second.y()),
                        Math.max(first.z(), second.z())));
    }

    public long sizeX() {
        return (long) max.x() - min.x() + 1L;
    }

    public long sizeY() {
        return (long) max.y() - min.y() + 1L;
    }

    public long sizeZ() {
        return (long) max.z() - min.z() + 1L;
    }

    public long volume() {
        return Math.multiplyExact(Math.multiplyExact(sizeX(), sizeY()), sizeZ());
    }

    public boolean contains(int x, int y, int z) {
        return x >= min.x() && x <= max.x()
                && y >= min.y() && y <= max.y()
                && z >= min.z() && z <= max.z();
    }

    public BlockPoint toLocal(BlockPoint point) {
        Objects.requireNonNull(point, "point");
        requireSameDimension(min, point);
        return new BlockPoint(min.dimension(),
                Math.subtractExact(point.x(), min.x()),
                Math.subtractExact(point.y(), min.y()),
                Math.subtractExact(point.z(), min.z()));
    }

    private static void requireSameDimension(BlockPoint first, BlockPoint second) {
        if (!first.dimension().equals(second.dimension())) {
            throw new IllegalArgumentException("Selection points must be in the same dimension");
        }
    }
}
