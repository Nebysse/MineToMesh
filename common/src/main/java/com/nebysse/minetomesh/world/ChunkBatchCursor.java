package com.nebysse.minetomesh.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ChunkBatchCursor {
    private static final int MACRO_SIZE = 4;

    private final ChunkRange range;
    private int nextMacroX;
    private int nextMacroZ;
    private boolean hasNextMacro = true;
    private List<ChunkCoordinate> currentMacro = List.of();
    private int currentMacroOffset;
    private long emitted;
    private BatchBounds currentBatchBounds;

    ChunkBatchCursor(ChunkRange range) {
        this.range = Objects.requireNonNull(range, "range");
        nextMacroX = range.minX();
        nextMacroZ = range.minZ();
    }

    public List<ChunkCoordinate> next(int batchSize) {
        ChunkRange.validateBatchSize(batchSize);
        if (exhausted()) {
            return List.of();
        }
        ensureCurrentMacro();
        int end = Math.min(currentMacro.size(), currentMacroOffset + batchSize);
        List<ChunkCoordinate> batch = List.copyOf(
                currentMacro.subList(currentMacroOffset, end));
        currentMacroOffset = end;
        emitted = Math.addExact(emitted, batch.size());
        currentBatchBounds = BatchBounds.of(batch);
        return batch;
    }

    public BatchBounds currentBatchBounds() {
        if (currentBatchBounds == null) {
            throw new IllegalStateException("No chunk batch has been emitted");
        }
        return currentBatchBounds;
    }

    public long emitted() {
        return emitted;
    }

    public long remaining() {
        return range.totalChunks() - emitted;
    }

    public boolean exhausted() {
        return emitted == range.totalChunks();
    }

    private void ensureCurrentMacro() {
        if (currentMacroOffset < currentMacro.size()) {
            return;
        }
        if (!hasNextMacro) {
            throw new IllegalStateException("Chunk cursor is exhausted");
        }
        int macroMaxX = (int) Math.min((long) range.maxX(), (long) nextMacroX + MACRO_SIZE - 1L);
        int macroMaxZ = (int) Math.min((long) range.maxZ(), (long) nextMacroZ + MACRO_SIZE - 1L);
        List<ChunkCoordinate> next = new ArrayList<>(MACRO_SIZE * MACRO_SIZE);
        for (int x = nextMacroX; x <= macroMaxX; x++) {
            for (int z = nextMacroZ; z <= macroMaxZ; z++) {
                next.add(new ChunkCoordinate(x, z));
            }
        }
        currentMacro = List.copyOf(next);
        currentMacroOffset = 0;
        advanceMacroOrigin();
    }

    private void advanceMacroOrigin() {
        long nextZ = (long) nextMacroZ + MACRO_SIZE;
        if (nextZ <= range.maxZ()) {
            nextMacroZ = (int) nextZ;
            return;
        }
        nextMacroZ = range.minZ();
        long nextX = (long) nextMacroX + MACRO_SIZE;
        if (nextX <= range.maxX()) {
            nextMacroX = (int) nextX;
        } else {
            hasNextMacro = false;
        }
    }

    public record BatchBounds(
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            ChunkCoordinate center) {
        public BatchBounds {
            Objects.requireNonNull(center, "center");
            if (minX > maxX || minZ > maxZ) {
                throw new IllegalArgumentException("Batch bounds must be normalized");
            }
        }

        public int width() {
            return maxX - minX + 1;
        }

        public int depth() {
            return maxZ - minZ + 1;
        }

        private static BatchBounds of(List<ChunkCoordinate> chunks) {
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("Batch bounds require at least one chunk");
            }
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (ChunkCoordinate chunk : chunks) {
                minX = Math.min(minX, chunk.x());
                maxX = Math.max(maxX, chunk.x());
                minZ = Math.min(minZ, chunk.z());
                maxZ = Math.max(maxZ, chunk.z());
            }
            int centerX = minX + Math.floorDiv(maxX - minX, 2);
            int centerZ = minZ + Math.floorDiv(maxZ - minZ, 2);
            return new BatchBounds(
                    minX, maxX, minZ, maxZ,
                    new ChunkCoordinate(centerX, centerZ));
        }
    }
}
