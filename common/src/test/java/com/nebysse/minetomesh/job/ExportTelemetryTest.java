package com.nebysse.minetomesh.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ExportTelemetryTest {
    @Test
    void eachCounterMapsToItsApprovedProgressBand() {
        ExportTelemetry telemetry = initialized();
        assertSnapshot(telemetry, ExportStage.PREPARING_SERVER, 0);

        telemetry.serverPrepared();
        assertSnapshot(telemetry, ExportStage.SYNCHRONIZING_CHUNKS, 5);

        telemetry.chunksSynchronized(50);
        assertSnapshot(telemetry, ExportStage.SYNCHRONIZING_CHUNKS, 12);

        telemetry.positionsCaptured(500, "section/5");
        assertSnapshot(telemetry, ExportStage.CAPTURING, 42);

        telemetry.chunksProcessed(25);
        assertSnapshot(telemetry, ExportStage.PROCESSING, 68);

        telemetry.batchesPersisted(12);
        assertSnapshot(telemetry, ExportStage.WRITING, 87);

        telemetry.finalizing();
        assertSnapshot(telemetry, ExportStage.FINALIZING, 95);
    }

    @Test
    void overlappingAndLateUpdatesNeverRegressProgressOrStage() {
        ExportTelemetry telemetry = initialized();
        telemetry.positionsCaptured(900, "ahead");
        int capturePercent = telemetry.snapshot().percent();
        telemetry.chunksSynchronized(10);
        telemetry.positionsCaptured(100, "late");

        assertEquals(capturePercent, telemetry.snapshot().percent());
        assertEquals(ExportStage.CAPTURING, telemetry.snapshot().stage());
        assertEquals("late", telemetry.snapshot().currentObjectId());
    }

    @Test
    void snapshotCarriesBatchThreadQueueAndElapsedFields() {
        ExportTelemetry telemetry = initialized();
        telemetry.batchSequence(7);
        telemetry.queues(3, 2);
        telemetry.elapsed(Duration.ofSeconds(9));

        ExportProgressSnapshot snapshot = telemetry.snapshot();
        assertEquals(7, snapshot.batchSequence());
        assertEquals(25, snapshot.totalBatches());
        assertEquals(4, snapshot.configuredWorkers());
        assertEquals(6, snapshot.effectiveWorkers());
        assertEquals(3, snapshot.processingQueueDepth());
        assertEquals(2, snapshot.writingQueueDepth());
        assertEquals(Duration.ofSeconds(9), snapshot.elapsed());
    }

    @Test
    void zeroTotalsAreSafeAndCompleteTheirEnteredBand() {
        ExportTelemetry telemetry = new ExportTelemetry();
        telemetry.initialize(0, 0, 0, 1, 1);
        telemetry.serverPrepared();
        telemetry.chunksSynchronized(0);
        telemetry.positionsCaptured(0, "entities");
        telemetry.chunksProcessed(0);
        telemetry.batchesPersisted(0);

        assertEquals(95, telemetry.snapshot().percent());
        assertEquals(ExportStage.WRITING, telemetry.snapshot().stage());
    }

    @Test
    void oneHundredRequiresAllThreeFinalizationSteps() {
        ExportTelemetry telemetry = initialized();
        telemetry.serverPrepared();
        telemetry.chunksSynchronized(50);
        telemetry.positionsCaptured(500, "section/5");
        telemetry.chunksProcessed(25);
        telemetry.batchesPersisted(12);

        telemetry.finalizationStep(ExportTelemetry.FinalizationStep.PUBLISHED);
        assertTrue(telemetry.snapshot().percent() < 100);
        telemetry.finalizationStep(ExportTelemetry.FinalizationStep.SERVER_RESTORED);
        assertTrue(telemetry.snapshot().percent() < 100);
        telemetry.finalizationStep(ExportTelemetry.FinalizationStep.TRACKING_RESTORED);
        assertEquals(100, telemetry.snapshot().percent());
        assertEquals(ExportStage.FINALIZING, telemetry.snapshot().stage());
    }

    @Test
    void rejectsInvalidInitializationAndRuntimeCounters() {
        ExportTelemetry telemetry = new ExportTelemetry();
        assertThrows(IllegalArgumentException.class,
                () -> telemetry.initialize(-1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> telemetry.initialize(1, 1, 1, 0, 1));

        telemetry.initialize(1, 1, 1, 1, 1);
        assertThrows(IllegalArgumentException.class,
                () -> telemetry.chunksProcessed(-1));
        assertThrows(IllegalArgumentException.class,
                () -> telemetry.queues(-1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> telemetry.elapsed(Duration.ofSeconds(-1)));
    }

    private static ExportTelemetry initialized() {
        ExportTelemetry telemetry = new ExportTelemetry();
        telemetry.initialize(100, 25, 1_000, 4, 6);
        return telemetry;
    }

    private static void assertSnapshot(
            ExportTelemetry telemetry, ExportStage stage, int percent) {
        assertEquals(stage, telemetry.snapshot().stage());
        assertEquals(percent, telemetry.snapshot().percent());
    }
}
