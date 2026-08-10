package com.onecuber.mcgltf.job;

import java.time.Duration;
import java.util.Objects;

public record ExportProgress(
        JobState state,
        long completedWorkItems,
        long totalWorkItems,
        int queueDepth,
        Duration elapsed,
        String currentObjectId) {
    public ExportProgress {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(elapsed, "elapsed");
        Objects.requireNonNull(currentObjectId, "currentObjectId");
        if (completedWorkItems < 0 || totalWorkItems < 0 || completedWorkItems > totalWorkItems) {
            throw new IllegalArgumentException("Progress work item counts are invalid");
        }
        if (queueDepth < 0) {
            throw new IllegalArgumentException("Queue depth must not be negative");
        }
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("Elapsed duration must not be negative");
        }
    }
}
