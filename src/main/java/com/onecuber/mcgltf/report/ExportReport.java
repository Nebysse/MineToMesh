package com.onecuber.mcgltf.report;

import com.onecuber.mcgltf.scene.BatchCounters;
import com.onecuber.mcgltf.scene.Diagnostic;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record ExportReport(
        String status,
        String snapshotMode,
        String dimension,
        int[] min,
        int[] max,
        int[] origin,
        long volume,
        long startGameTime,
        long endGameTime,
        BatchCounters counters,
        List<MissingChunk> missingChunks,
        List<Diagnostic> diagnostics,
        Map<String, Long> timingsMillis) {
    public ExportReport {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(snapshotMode, "snapshotMode");
        Objects.requireNonNull(dimension, "dimension");
        min = copyCoordinate(min, "min");
        max = copyCoordinate(max, "max");
        origin = copyCoordinate(origin, "origin");
        if (volume < 0) {
            throw new IllegalArgumentException("Volume must not be negative");
        }
        Objects.requireNonNull(counters, "counters");
        missingChunks = List.copyOf(missingChunks);
        diagnostics = List.copyOf(diagnostics);
        timingsMillis = Collections.unmodifiableMap(new TreeMap<>(timingsMillis));
    }

    public int schemaVersion() {
        return 1;
    }

    @Override
    public int[] min() {
        return min.clone();
    }

    @Override
    public int[] max() {
        return max.clone();
    }

    @Override
    public int[] origin() {
        return origin.clone();
    }

    private static int[] copyCoordinate(int[] coordinate, String name) {
        Objects.requireNonNull(coordinate, name);
        if (coordinate.length != 3) {
            throw new IllegalArgumentException(name + " must contain exactly three coordinates");
        }
        return coordinate.clone();
    }

    public record MissingChunk(int chunkX, int chunkZ) implements Comparable<MissingChunk> {
        @Override
        public int compareTo(MissingChunk other) {
            int x = Integer.compare(chunkX, other.chunkX);
            return x != 0 ? x : Integer.compare(chunkZ, other.chunkZ);
        }
    }
}
