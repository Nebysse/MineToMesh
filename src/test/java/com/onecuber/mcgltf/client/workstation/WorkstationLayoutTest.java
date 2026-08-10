package com.onecuber.mcgltf.client.workstation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onecuber.mcgltf.workstation.Endpoint;
import java.lang.reflect.Method;
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
    void coordinateGroupsAndOverlayControlDoNotOverlap() throws Exception {
        Method title = ExportWorkstationScreen.Layout.class.getDeclaredMethod(
                "endpointTitle", Endpoint.class);
        Method field = ExportWorkstationScreen.Layout.class.getDeclaredMethod(
                "coordinateField", int.class);
        Method overlay = ExportWorkstationScreen.Layout.class.getDeclaredMethod(
                "overlayButton");
        title.setAccessible(true);
        field.setAccessible(true);
        overlay.setAccessible(true);

        ExportWorkstationScreen.Rect firstTitle =
                (ExportWorkstationScreen.Rect) title.invoke(null, Endpoint.FIRST);
        ExportWorkstationScreen.Rect secondTitle =
                (ExportWorkstationScreen.Rect) title.invoke(null, Endpoint.SECOND);
        ExportWorkstationScreen.Rect overlayButton =
                (ExportWorkstationScreen.Rect) overlay.invoke(null);

        for (int index = 0; index < 6; index++) {
            ExportWorkstationScreen.Rect coordinate =
                    (ExportWorkstationScreen.Rect) field.invoke(null, index);
            assertFalse(coordinate.intersects(firstTitle), "field " + index + " overlaps first title");
            assertFalse(coordinate.intersects(secondTitle), "field " + index + " overlaps second title");
            assertFalse(coordinate.intersects(overlayButton), "field " + index + " overlaps overlay button");
            assertTrue(LayoutBounds.contains(ExportWorkstationScreen.Layout.LEFT, coordinate));
        }
        assertTrue(LayoutBounds.contains(ExportWorkstationScreen.Layout.LEFT, overlayButton));
    }

    @Test
    void rectComputesBounds() {
        ExportWorkstationScreen.Rect rect = new ExportWorkstationScreen.Rect(4, 24, 208, 166);
        assertEquals(212, rect.right());
        assertEquals(190, rect.bottom());
    }

    private static final class LayoutBounds {
        private static boolean contains(
                ExportWorkstationScreen.Rect outer, ExportWorkstationScreen.Rect inner) {
            return inner.x() >= outer.x() && inner.y() >= outer.y()
                    && inner.right() <= outer.right() && inner.bottom() <= outer.bottom();
        }
    }
}
