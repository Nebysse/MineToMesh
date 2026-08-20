package com.nebysse.minetomesh.client.config;

import com.nebysse.minetomesh.job.ExportExecutionPolicy;
import java.util.Objects;

public record ClientExportSettings(int workerThreads) {
    public ClientExportSettings {
        if (workerThreads < 1) {
            throw new IllegalArgumentException("Worker threads must be positive");
        }
    }

    public static ClientExportSettings defaults(int availableProcessors) {
        return new ClientExportSettings(
                ExportExecutionPolicy.defaultWorkers(availableProcessors));
    }

    public static ClientExportSettings clamped(int requested, int availableProcessors) {
        return new ClientExportSettings(
                ExportExecutionPolicy.clampWorkers(requested, availableProcessors));
    }

    public int effectiveWorkers(int batchChunkCount, int availableProcessors) {
        return ExportExecutionPolicy.effectiveWorkers(
                workerThreads, batchChunkCount, availableProcessors);
    }

    public ClientExportSettings withWorkerThreads(int value) {
        Objects.requireNonNull(value, "value");
        return new ClientExportSettings(value);
    }
}
