package com.onecuber.mcgltf.client.workstation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorkstationLayoutTest {
    @Test
    void layoutMatchesApprovedDesignCoordinates() {
        assertEquals(new ExportWorkstationScreen.Rect(0, 0, 384, 20),
                ExportWorkstationScreen.Layout.HEADER);
        assertEquals(new ExportWorkstationScreen.Rect(4, 24, 208, 166),
                ExportWorkstationScreen.Layout.LEFT);
        assertEquals(new ExportWorkstationScreen.Rect(216, 24, 164, 166),
                ExportWorkstationScreen.Layout.RIGHT);
        assertEquals(new ExportWorkstationScreen.Rect(4, 194, 376, 18),
                ExportWorkstationScreen.Layout.LOG);
    }

    @Test
    void panelsDoNotOverlap() {
        assertFalse(ExportWorkstationScreen.Layout.LEFT.intersects(
                ExportWorkstationScreen.Layout.RIGHT));
        assertFalse(ExportWorkstationScreen.Layout.LEFT.intersects(
                ExportWorkstationScreen.Layout.LOG));
        assertFalse(ExportWorkstationScreen.Layout.RIGHT.intersects(
                ExportWorkstationScreen.Layout.LOG));
    }

    @Test
    void headerSpansFullWidthAboveLeftPanel() {
        assertTrue(ExportWorkstationScreen.Layout.HEADER.x()
                <= ExportWorkstationScreen.Layout.LEFT.x());
        assertTrue(ExportWorkstationScreen.Layout.HEADER.right()
                >= ExportWorkstationScreen.Layout.RIGHT.right());
        assertTrue(ExportWorkstationScreen.Layout.HEADER.bottom()
                <= ExportWorkstationScreen.Layout.LEFT.y());
    }

    @Test
    void rectComputesBounds() {
        ExportWorkstationScreen.Rect rect = new ExportWorkstationScreen.Rect(4, 24, 208, 166);
        assertEquals(212, rect.right());
        assertEquals(190, rect.bottom());
    }
}
