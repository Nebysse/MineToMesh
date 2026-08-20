package com.nebysse.minetomesh.world;

import java.util.Objects;

public record ChunkRange(int minX, int maxX, int minZ, int maxZ) {
    private static final int MACRO_SIZE = 4;

    public ChunkRange {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("Chunk range bounds must be normalized");
        }
    }

    public static ChunkRange from(Selection selection) {
        Objects.requireNonNull(selection, "selection");
        return new ChunkRange(
                Math.floorDiv(selection.min().x(), 16),
                Math.floorDiv(selection.max().x(), 16),
                Math.floorDiv(selection.min().z(), 16),
                Math.floorDiv(selection.max().z(), 16));
    }

    public long totalChunks() {
        return Math.multiplyExact(width(), depth());
    }

    public long totalBatches(int batchSize) {
        validateBatchSize(batchSize);
        long width = width();
        long depth = depth();
        long fullX = width / MACRO_SIZE;
        long fullZ = depth / MACRO_SIZE;
        long remainderX = width % MACRO_SIZE;
        long remainderZ = depth % MACRO_SIZE;

        long total = Math.multiplyExact(
                Math.multiplyExact(fullX, fullZ), ceilDiv(16, batchSize));
        if (remainderZ > 0) {
            total = Math.addExact(total, Math.multiplyExact(
                    fullX, ceilDiv(Math.multiplyExact(4, remainderZ), batchSize)));
        }
        if (remainderX > 0) {
            total = Math.addExact(total, Math.multiplyExact(
                    fullZ, ceilDiv(Math.multiplyExact(remainderX, 4), batchSize)));
        }
        if (remainderX > 0 && remainderZ > 0) {
            total = Math.addExact(total, ceilDiv(
                    Math.multiplyExact(remainderX, remainderZ), batchSize));
        }
        return total;
    }

    public ChunkBatchCursor cursor() {
        return new ChunkBatchCursor(this);
    }

    long width() {
        return (long) maxX - minX + 1L;
    }

    long depth() {
        return (long) maxZ - minZ + 1L;
    }

    static void validateBatchSize(int batchSize) {
        if (batchSize < 1 || batchSize > 16) {
            throw new IllegalArgumentException("Batch size must be within 1..16");
        }
    }

    private static long ceilDiv(long value, long divisor) {
        return Math.addExact(value, divisor - 1L) / divisor;
    }
}
