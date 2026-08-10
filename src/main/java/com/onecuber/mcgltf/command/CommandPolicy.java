package com.onecuber.mcgltf.command;

import com.onecuber.mcgltf.job.ExportProgress;
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
        int percentage = progress.totalWorkItems() == 0
                ? 0
                : (int) Math.min(100L,
                        Math.multiplyExact(progress.completedWorkItems(), 100L)
                                / progress.totalWorkItems());
        return progress.state().name()
                + " " + percentage + "%"
                + " queue=" + progress.queueDepth()
                + " current=" + progress.currentObjectId();
    }
}
