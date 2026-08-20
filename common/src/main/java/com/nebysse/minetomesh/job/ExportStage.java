package com.nebysse.minetomesh.job;

import java.util.Objects;

public enum ExportStage {
    IDLE("minetomesh.export.stage.idle", 0, 0),
    PREPARING_SERVER("minetomesh.export.stage.preparing_server", 0, 5),
    SYNCHRONIZING_CHUNKS("minetomesh.export.stage.synchronizing_chunks", 5, 20),
    CAPTURING("minetomesh.export.stage.capturing", 20, 65),
    PROCESSING("minetomesh.export.stage.processing", 65, 80),
    WRITING("minetomesh.export.stage.writing", 80, 95),
    FINALIZING("minetomesh.export.stage.finalizing", 95, 100);

    private final String translationKey;
    private final int startPercent;
    private final int endPercent;

    ExportStage(String translationKey, int startPercent, int endPercent) {
        this.translationKey = Objects.requireNonNull(translationKey, "translationKey");
        this.startPercent = startPercent;
        this.endPercent = endPercent;
    }

    public String translationKey() {
        return translationKey;
    }

    public int startPercent() {
        return startPercent;
    }

    public int endPercent() {
        return endPercent;
    }

    static ExportStage fromLegacyKey(String key) {
        return switch (Objects.requireNonNull(key, "key")) {
            case "capturing" -> CAPTURING;
            case "draining" -> WRITING;
            case "textures", "documents", "report", "committed" -> FINALIZING;
            default -> IDLE;
        };
    }
}
