package com.nebysse.minetomesh.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nebysse.minetomesh.job.ExportProgress;
import com.nebysse.minetomesh.job.ExportProgressSnapshot;
import com.nebysse.minetomesh.job.ExportStage;
import com.nebysse.minetomesh.job.JobState;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CommandPolicyTest {
    @Test
    void volumeConfirmationUsesTheStableSoftLimit() {
        assertFalse(CommandPolicy.requiresConfirmation(CommandPolicy.SOFT_VOLUME_LIMIT));
        assertTrue(CommandPolicy.requiresConfirmation(CommandPolicy.SOFT_VOLUME_LIMIT + 1));
    }

    @Test
    void statusUsesTheSharedSnapshotWithoutRecomputingPercentage() {
        ExportProgressSnapshot snapshot = new ExportProgressSnapshot(
                ExportStage.PROCESSING,
                73,
                7,
                20,
                30,
                40,
                600,
                1_000,
                28,
                6,
                8,
                4,
                3,
                2,
                "chunk[3,-8]",
                Duration.ofSeconds(12));
        ExportProgress progress = new ExportProgress(
                JobState.CAPTURING, 1, 100, 99, Duration.ZERO, "legacy", snapshot);

        assertEquals(
                "CAPTURING 73% stage=minetomesh.export.stage.processing"
                        + " chunks=30/40 workers=4/8"
                        + " processingQueue=3 writingQueue=2 current=chunk[3,-8]",
                CommandPolicy.formatStatus(progress));
    }
}
