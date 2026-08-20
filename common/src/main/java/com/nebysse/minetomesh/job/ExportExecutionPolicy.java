package com.nebysse.minetomesh.job;

public final class ExportExecutionPolicy {
    public static final int DEFAULT_BATCH_CHUNKS = 4;
    public static final int MIN_BATCH_CHUNKS = 1;
    public static final int MAX_BATCH_CHUNKS = 16;
    public static final int MAX_WORKERS = 16;

    private ExportExecutionPolicy() {
    }

    public static int validateBatchChunks(int value) {
        if (value < MIN_BATCH_CHUNKS || value > MAX_BATCH_CHUNKS) {
            throw new IllegalArgumentException("Batch chunk count must be within 1..16");
        }
        return value;
    }

    public static int maxWorkers(int availableProcessors) {
        int processors = Math.max(1, availableProcessors);
        return Math.max(1, Math.min(MAX_WORKERS, processors - 2));
    }

    public static int defaultWorkers(int availableProcessors) {
        return Math.min(4, maxWorkers(availableProcessors));
    }

    public static int clampWorkers(int requested, int availableProcessors) {
        return Math.max(1, Math.min(requested, maxWorkers(availableProcessors)));
    }

    public static int effectiveWorkers(
            int requested, int batchChunkCount, int availableProcessors) {
        return Math.min(
                clampWorkers(requested, availableProcessors),
                validateBatchChunks(batchChunkCount));
    }
}
