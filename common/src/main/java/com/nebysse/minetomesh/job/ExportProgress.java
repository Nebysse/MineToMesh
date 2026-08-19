package com.nebysse.minetomesh.job;

import java.time.Duration;
import java.util.Objects;

public record ExportProgress(
        JobState state,
        long completedWorkItems,
        long totalWorkItems,
        int queueDepth,
        Duration elapsed,
        String currentObjectId,
        int percent,
        String stageKey) {
    public ExportProgress {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(elapsed, "elapsed");
        Objects.requireNonNull(currentObjectId, "currentObjectId");
        Objects.requireNonNull(stageKey, "stageKey");
        if (completedWorkItems < 0 || totalWorkItems < 0 || completedWorkItems > totalWorkItems) {
            throw new IllegalArgumentException("Progress work item counts are invalid");
        }
        if (queueDepth < 0) {
            throw new IllegalArgumentException("Queue depth must not be negative");
        }
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("Elapsed duration must not be negative");
        }
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Progress percent must be within 0..100");
        }
        if (stageKey.isBlank()) {
            throw new IllegalArgumentException("Progress stage key must not be blank");
        }
    }

    public ExportProgress(
            JobState state,
            long completedWorkItems,
            long totalWorkItems,
            int queueDepth,
            Duration elapsed,
            String currentObjectId) {
        this(state, completedWorkItems, totalWorkItems, queueDepth,
                elapsed, currentObjectId, 0, "idle");
    }
}
