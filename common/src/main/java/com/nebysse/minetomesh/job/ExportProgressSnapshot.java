package com.nebysse.minetomesh.job;

import java.time.Duration;
import java.util.Objects;

public record ExportProgressSnapshot(
        ExportStage stage,
        int percent,
        long batchSequence,
        long totalBatches,
        long synchronizedChunks,
        long totalChunks,
        long capturedPositions,
        long totalPositions,
        long processedChunks,
        long persistedBatches,
        int configuredWorkers,
        int effectiveWorkers,
        int processingQueueDepth,
        int writingQueueDepth,
        String currentObjectId,
        Duration elapsed) {
    public ExportProgressSnapshot {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(currentObjectId, "currentObjectId");
        Objects.requireNonNull(elapsed, "elapsed");
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Progress percent must be within 0..100");
        }
        requireNonNegative(batchSequence, "batchSequence");
        requireNonNegative(totalBatches, "totalBatches");
        requireNonNegative(synchronizedChunks, "synchronizedChunks");
        requireNonNegative(totalChunks, "totalChunks");
        requireNonNegative(capturedPositions, "capturedPositions");
        requireNonNegative(totalPositions, "totalPositions");
        requireNonNegative(processedChunks, "processedChunks");
        requireNonNegative(persistedBatches, "persistedBatches");
        requireNonNegative(configuredWorkers, "configuredWorkers");
        requireNonNegative(effectiveWorkers, "effectiveWorkers");
        requireNonNegative(processingQueueDepth, "processingQueueDepth");
        requireNonNegative(writingQueueDepth, "writingQueueDepth");
        if (synchronizedChunks > totalChunks
                || processedChunks > totalChunks
                || capturedPositions > totalPositions
                || persistedBatches > Math.max(totalBatches, totalPositions)) {
            throw new IllegalArgumentException("Completed progress exceeds its total");
        }
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("Elapsed duration must not be negative");
        }
    }

    public String stageKey() {
        return stage.translationKey();
    }

    public int queueDepth() {
        return writingQueueDepth;
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
