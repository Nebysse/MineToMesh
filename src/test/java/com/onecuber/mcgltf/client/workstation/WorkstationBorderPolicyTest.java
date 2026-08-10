package com.onecuber.mcgltf.client.workstation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class WorkstationBorderPolicyTest {
    @Test
    void convertsEightPhysicalPixelsToNearestLogicalBorder() {
        assertEquals(8, WorkstationBorderPolicy.logicalBorder(
                8, 1.0, 64, 64, 64, 64));
        assertEquals(4, WorkstationBorderPolicy.logicalBorder(
                8, 2.0, 64, 64, 64, 64));
        assertEquals(3, WorkstationBorderPolicy.logicalBorder(
                8, 3.0, 64, 64, 64, 64));
        assertEquals(2, WorkstationBorderPolicy.logicalBorder(
                8, 4.0, 64, 64, 64, 64));
        assertEquals(1, WorkstationBorderPolicy.logicalBorder(
                8, 6.0, 64, 64, 64, 64));
    }

    @Test
    void alwaysLeavesAtLeastOnePixelOfSourceAndDestinationCenter() {
        assertEquals(7, WorkstationBorderPolicy.logicalBorder(
                8, 1.0, 33, 16, 148, 16));
        assertEquals(2, WorkstationBorderPolicy.logicalBorder(
                8, 1.0, 5, 5, 5, 5));
    }

    @Test
    void borderlessStylesRemainBorderless() {
        assertEquals(0, WorkstationBorderPolicy.logicalBorder(
                0, 4.0, 144, 3, 192, 3));
    }

    @Test
    void rejectsInvalidScaleAndDimensions() {
        assertThrows(IllegalArgumentException.class, () ->
                WorkstationBorderPolicy.logicalBorder(8, 0.0, 16, 16, 16, 16));
        assertThrows(IllegalArgumentException.class, () ->
                WorkstationBorderPolicy.logicalBorder(-1, 2.0, 16, 16, 16, 16));
        assertThrows(IllegalArgumentException.class, () ->
                WorkstationBorderPolicy.logicalBorder(8, 2.0, 0, 16, 16, 16));
    }
}
