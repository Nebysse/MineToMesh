package com.nebysse.minetomesh.job;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public record ExportSummary(
        String status,
        Optional<Path> outputDirectory,
        long nodeCount,
        long primitiveCount,
        long textureCount,
        long warningCount,
        Duration elapsed,
        Optional<String> failureReason) {
    public ExportSummary {
        Objects.requireNonNull(status, "status");
        outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
        failureReason = Objects.requireNonNull(failureReason, "failureReason");
        Objects.requireNonNull(elapsed, "elapsed");
        if (nodeCount < 0 || primitiveCount < 0 || textureCount < 0 || warningCount < 0) {
            throw new IllegalArgumentException("Summary counts must not be negative");
        }
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("Summary elapsed must not be negative");
        }
        boolean successful = outputDirectory.isPresent();
        if (successful == failureReason.isPresent()) {
            throw new IllegalArgumentException(
                    "A summary must be either successful or failed");
        }
    }
}
