package com.nebysse.minetomesh.client.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ExportWandBorderPolicyTest {
    @Test
    void convertsEightPhysicalPixelsAtActiveGuiScale() {
        assertEquals(8, ExportWandBorderPolicy.logicalBorder(
                8, 1.0, 64, 64, 64, 64));
        assertEquals(4, ExportWandBorderPolicy.logicalBorder(
                8, 2.0, 64, 64, 64, 64));
        assertEquals(3, ExportWandBorderPolicy.logicalBorder(
                8, 3.0, 64, 64, 64, 64));
        assertEquals(2, ExportWandBorderPolicy.logicalBorder(
                8, 4.0, 64, 64, 64, 64));
    }

    @Test
    void keepsAValidCenterAndRejectsBadInputs() {
        assertEquals(7, ExportWandBorderPolicy.logicalBorder(
                8, 1.0, 33, 16, 148, 16));
        assertEquals(0, ExportWandBorderPolicy.logicalBorder(
                0, 4.0, 144, 3, 192, 3));
        assertThrows(IllegalArgumentException.class, () ->
                ExportWandBorderPolicy.logicalBorder(8, 0.0, 16, 16, 16, 16));
    }
}
