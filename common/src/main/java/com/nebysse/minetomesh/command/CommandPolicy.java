package com.nebysse.minetomesh.command;

import com.nebysse.minetomesh.job.ExportProgress;
import com.nebysse.minetomesh.job.ExportProgressSnapshot;
import java.util.Objects;

public final class CommandPolicy {
    public static final long SOFT_VOLUME_LIMIT = 4_194_304L;

    private CommandPolicy() {
    }

    public static boolean requiresConfirmation(long volume) {
        if (volume < 0) {
            throw new IllegalArgumentException("Volume must not be negative");
        }
        return volume > SOFT_VOLUME_LIMIT;
    }

    public static String formatStatus(ExportProgress progress) {
        Objects.requireNonNull(progress, "progress");
        ExportProgressSnapshot snapshot = progress.snapshot();
        return progress.state().name()
                + " " + snapshot.percent() + "%"
                + " stage=" + snapshot.stageKey()
                + " chunks=" + snapshot.synchronizedChunks()
                + "/" + snapshot.totalChunks()
                + " workers=" + snapshot.effectiveWorkers()
                + "/" + snapshot.configuredWorkers()
                + " processingQueue=" + snapshot.processingQueueDepth()
                + " writingQueue=" + snapshot.writingQueueDepth()
                + " current=" + snapshot.currentObjectId();
    }
}
