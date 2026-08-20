package com.nebysse.minetomesh.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ExportExecutionPolicyTest {
    @Test
    void validatesRollingBatchRange() {
        assertEquals(1, ExportExecutionPolicy.validateBatchChunks(1));
        assertEquals(4, ExportExecutionPolicy.validateBatchChunks(4));
        assertEquals(16, ExportExecutionPolicy.validateBatchChunks(16));
        assertThrows(IllegalArgumentException.class,
                () -> ExportExecutionPolicy.validateBatchChunks(0));
        assertThrows(IllegalArgumentException.class,
                () -> ExportExecutionPolicy.validateBatchChunks(17));
    }

    @Test
    void reservesTwoCpuThreadsAndCapsAtSixteen() {
        assertEquals(1, ExportExecutionPolicy.maxWorkers(1));
        assertEquals(1, ExportExecutionPolicy.maxWorkers(2));
        assertEquals(2, ExportExecutionPolicy.maxWorkers(4));
        assertEquals(6, ExportExecutionPolicy.maxWorkers(8));
        assertEquals(16, ExportExecutionPolicy.maxWorkers(64));
    }

    @Test
    void defaultUsesAtMostFourWorkers() {
        assertEquals(1, ExportExecutionPolicy.defaultWorkers(2));
        assertEquals(2, ExportExecutionPolicy.defaultWorkers(4));
        assertEquals(4, ExportExecutionPolicy.defaultWorkers(8));
        assertEquals(4, ExportExecutionPolicy.defaultWorkers(64));
    }

    @Test
    void clampsConfiguredWorkersToMachineAndBatch() {
        assertEquals(1, ExportExecutionPolicy.clampWorkers(0, 8));
        assertEquals(6, ExportExecutionPolicy.clampWorkers(99, 8));
        assertEquals(3, ExportExecutionPolicy.clampWorkers(3, 8));
        assertEquals(4, ExportExecutionPolicy.effectiveWorkers(14, 4, 32));
        assertEquals(2, ExportExecutionPolicy.effectiveWorkers(4, 16, 4));
    }
}
