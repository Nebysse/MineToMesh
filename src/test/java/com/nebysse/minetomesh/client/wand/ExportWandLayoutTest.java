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
        assertEquals(new ExportWandScreen.Rect(4, 24, 208, 166),
                ExportWandScreen.Layout.LEFT);
        assertEquals(new ExportWandScreen.Rect(216, 24, 164, 166),
                ExportWandScreen.Layout.RIGHT);
        for (int index = 0; index < 6; index++) {
            ExportWandScreen.Rect field =
                    ExportWandScreen.Layout.coordinateField(index);
            assertEquals(116, field.width());
            assertTrue(contains(ExportWandScreen.Layout.LEFT, field));
        }
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
