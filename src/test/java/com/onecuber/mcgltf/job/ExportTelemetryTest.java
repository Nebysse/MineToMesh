package com.onecuber.mcgltf.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExportTelemetryTest {
    @Test
    void captureMapsOntoZeroToEightyAndWriterFloorWins() {
        ExportTelemetry telemetry = new ExportTelemetry();
        telemetry.capture(5, 10, "section/5", 1);
        telemetry.writerStage(ExportTelemetry.WriterStage.TEXTURES);
        telemetry.capture(4, 10, "late", 0);
        assertEquals(88, telemetry.snapshot().percent());
        assertEquals("textures", telemetry.snapshot().stageKey());
    }

    @Test
    void completionIsExactlyOneHundred() {
        ExportTelemetry telemetry = new ExportTelemetry();
        telemetry.writerStage(ExportTelemetry.WriterStage.COMMITTED);
        assertEquals(100, telemetry.snapshot().percent());
        assertEquals("committed", telemetry.snapshot().stageKey());
    }

    @Test
    void percentNeverDecreases() {
        ExportTelemetry telemetry = new ExportTelemetry();
        telemetry.capture(8, 10, "first", 3);
        int first = telemetry.snapshot().percent();
        telemetry.capture(1, 10, "later", 5);
        assertTrue(telemetry.snapshot().percent() >= first);
        telemetry.writerStage(ExportTelemetry.WriterStage.TEXTURES);
        telemetry.capture(0, 10, "after-writer", 0);
        assertEquals(88, telemetry.snapshot().percent());
        assertEquals("after-writer", telemetry.snapshot().currentObjectId());
    }
}
