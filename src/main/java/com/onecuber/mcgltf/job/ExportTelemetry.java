package com.onecuber.mcgltf.job;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class ExportTelemetry {
    private final AtomicReference<Snapshot> reference =
            new AtomicReference<>(new Snapshot(0, "idle", "", 0));

    public void capture(long completed, long total, String currentObjectId, int queueDepth) {
        Objects.requireNonNull(currentObjectId, "currentObjectId");
        reference.updateAndGet(previous -> {
            int candidate = percent(completed, total);
            return new Snapshot(
                    Math.max(previous.percent(), candidate),
                    candidate > previous.percent() ? "capturing" : previous.stageKey(),
                    currentObjectId,
                    queueDepth);
        });
    }

    public void writerStage(WriterStage stage) {
        Objects.requireNonNull(stage, "stage");
        reference.updateAndGet(previous -> new Snapshot(
                Math.max(previous.percent(), stage.floor),
                stage.key,
                previous.currentObjectId(),
                previous.queueDepth()));
    }

    public Snapshot snapshot() {
        return reference.get();
    }

    private static int percent(long completed, long total) {
        if (total <= 0) {
            return 0;
        }
        long scaled = Math.min(80L, Math.floorDiv(completed * 80L, total));
        return (int) Math.max(0L, scaled);
    }

    public enum WriterStage {
        DRAINING(80, "draining"),
        TEXTURES(88, "textures"),
        DOCUMENTS(93, "documents"),
        REPORT(97, "report"),
        COMMITTED(100, "committed");

        private final int floor;
        private final String key;

        WriterStage(int floor, String key) {
            this.floor = floor;
            this.key = key;
        }
    }

    public record Snapshot(
            int percent, String stageKey, String currentObjectId, int queueDepth) {
        public Snapshot {
            if (percent < 0 || percent > 100) {
                throw new IllegalArgumentException("Telemetry percent must be within 0..100");
            }
            Objects.requireNonNull(stageKey, "stageKey");
            Objects.requireNonNull(currentObjectId, "currentObjectId");
            if (stageKey.isBlank()) {
                throw new IllegalArgumentException("Telemetry stage key must not be blank");
            }
            if (queueDepth < 0) {
                throw new IllegalArgumentException("Telemetry queue depth must not be negative");
            }
        }
    }
}
