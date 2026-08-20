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
        ExportProgressSnapshot snapshot) {
    public ExportProgress {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(elapsed, "elapsed");
        Objects.requireNonNull(currentObjectId, "currentObjectId");
        Objects.requireNonNull(snapshot, "snapshot");
        if (completedWorkItems < 0
                || totalWorkItems < 0
                || completedWorkItems > totalWorkItems) {
            throw new IllegalArgumentException("Progress work item counts are invalid");
        }
        if (queueDepth < 0) {
            throw new IllegalArgumentException("Queue depth must not be negative");
        }
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("Elapsed duration must not be negative");
        }
    }

    public ExportProgress(
            JobState state,
            long completedWorkItems,
            long totalWorkItems,
            int queueDepth,
            Duration elapsed,
            String currentObjectId,
            int percent,
            String stageKey) {
        this(state, completedWorkItems, totalWorkItems, queueDepth,
                elapsed, currentObjectId, legacySnapshot(
                        completedWorkItems,
                        totalWorkItems,
                        queueDepth,
                        elapsed,
                        currentObjectId,
                        percent,
                        stageKey));
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

    public int percent() {
        return snapshot.percent();
    }

    public String stageKey() {
        return snapshot.stageKey();
    }

    private static ExportProgressSnapshot legacySnapshot(
            long completedWorkItems,
            long totalWorkItems,
            int queueDepth,
            Duration elapsed,
            String currentObjectId,
            int percent,
            String stageKey) {
        return new ExportProgressSnapshot(
                ExportStage.fromLegacyKey(stageKey),
                percent,
                0,
                0,
                0,
                0,
                completedWorkItems,
                totalWorkItems,
                0,
                0,
                0,
                0,
                0,
                queueDepth,
                currentObjectId,
                elapsed);
    }
}
