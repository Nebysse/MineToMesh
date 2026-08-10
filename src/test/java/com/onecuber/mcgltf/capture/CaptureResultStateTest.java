package com.onecuber.mcgltf.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onecuber.mcgltf.scene.BatchCounters;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CaptureResultStateTest {
    @Test
    void emptyAuxiliaryResultHasNoNodeOrPlaceholder() {
        BlockEntityCapture.CaptureResult result = new BlockEntityCapture.CaptureResult(
                Optional.empty(), CaptureState.EMPTY, List.of(), BatchCounters.ZERO);

        assertEquals(CaptureState.EMPTY, result.state());
        assertTrue(result.node().isEmpty());
        assertEquals(0, result.counters().placeholders());
    }

    @Test
    void blockModelResultExposesWhetherItProducedGeometry() {
        BlockModelExtractor.CaptureResult geometry = new BlockModelExtractor.CaptureResult(
                CaptureState.GEOMETRY, BatchCounters.ZERO, List.of());
        BlockModelExtractor.CaptureResult empty = new BlockModelExtractor.CaptureResult(
                CaptureState.EMPTY, BatchCounters.ZERO, List.of());

        assertTrue(geometry.hasGeometry());
        assertFalse(empty.hasGeometry());
    }
}
