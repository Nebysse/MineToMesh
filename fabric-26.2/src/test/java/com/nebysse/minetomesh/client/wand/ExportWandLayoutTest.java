package com.nebysse.minetomesh.client.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExportWandLayoutTest {
    @Test
    void approvedFrameAndWideCoordinateFieldsRemainInsideLeftPanel() {
        assertEquals(new ExportWandScreen.Rect(0, 0, 384, 20),
                ExportWandScreen.Layout.HEADER);
        assertEquals(new ExportWandScreen.Rect(4, 24, 208, 188),
                ExportWandScreen.Layout.LEFT);
        assertEquals(new ExportWandScreen.Rect(216, 24, 164, 188),
                ExportWandScreen.Layout.RIGHT);
        assertEquals(new ExportWandScreen.Rect(4, 216, 376, 18),
                ExportWandScreen.Layout.LOG);
        for (int index = 0; index < 6; index++) {
            ExportWandScreen.Rect field =
                    ExportWandScreen.Layout.coordinateField(index);
            assertEquals(116, field.width());
            assertTrue(contains(ExportWandScreen.Layout.LEFT, field));
        }
    }

    @Test
    void threeIndependentTogglesAreEqualAndContained() {
        assertEquals(new ExportWandScreen.Rect(
                        ExportWandScreen.Layout.LEFT.x() + 12,
                        ExportWandScreen.Layout.LEFT.y() + 144, 60, 16),
                ExportWandScreen.Layout.overlayButton());
        assertEquals(new ExportWandScreen.Rect(
                        ExportWandScreen.Layout.LEFT.x() + 74,
                        ExportWandScreen.Layout.LEFT.y() + 144, 60, 16),
                ExportWandScreen.Layout.lockedSelectionButton());
        assertEquals(new ExportWandScreen.Rect(
                        ExportWandScreen.Layout.LEFT.x() + 136,
                        ExportWandScreen.Layout.LEFT.y() + 144, 60, 16),
                ExportWandScreen.Layout.includePlayersButton());
        List<ExportWandScreen.Rect> toggles = List.of(
                ExportWandScreen.Layout.overlayButton(),
                ExportWandScreen.Layout.lockedSelectionButton(),
                ExportWandScreen.Layout.includePlayersButton());
        for (ExportWandScreen.Rect toggle : toggles) {
            assertTrue(contains(ExportWandScreen.Layout.LEFT, toggle));
        }
        assertFalse(toggles.get(0).intersects(toggles.get(1)));
        assertFalse(toggles.get(1).intersects(toggles.get(2)));
    }

    @Test
    void mergeToggleAndNumericFieldsStayInsidePanels() {
        ExportWandScreen.Rect merge = ExportWandScreen.Layout.chunkMergeButton();
        assertTrue(contains(ExportWandScreen.Layout.LEFT, merge));
        assertFalse(merge.intersects(ExportWandScreen.Layout.overlayButton()));
        assertFalse(merge.intersects(ExportWandScreen.Layout.includePlayersButton()));

        ExportWandScreen.Rect batch = ExportWandScreen.Layout.batchField();
        ExportWandScreen.Rect worker = ExportWandScreen.Layout.workerField();
        assertTrue(contains(ExportWandScreen.Layout.RIGHT, batch));
        assertTrue(contains(ExportWandScreen.Layout.RIGHT, worker));
        assertFalse(batch.intersects(worker));
        assertFalse(batch.intersects(ExportWandScreen.Layout.cancelButton()));
    }

    @Test
    void sixFieldsAndTwelveStepButtonsNeverIntersect() {
        List<ExportWandScreen.Rect> controls = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            controls.add(ExportWandScreen.Layout.coordinateField(index));
            controls.add(ExportWandScreen.Layout.stepUp(index));
            controls.add(ExportWandScreen.Layout.stepDown(index));
        }
        for (int first = 0; first < controls.size(); first++) {
            for (int second = first + 1; second < controls.size(); second++) {
                assertFalse(controls.get(first).intersects(controls.get(second)),
                        first + " overlaps " + second);
            }
        }
    }

    private static boolean contains(
            ExportWandScreen.Rect outer, ExportWandScreen.Rect inner) {
        return inner.x() >= outer.x() && inner.y() >= outer.y()
                && inner.right() <= outer.right()
                && inner.bottom() <= outer.bottom();
    }
}
