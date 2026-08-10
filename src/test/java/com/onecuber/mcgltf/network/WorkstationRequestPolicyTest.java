package com.onecuber.mcgltf.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onecuber.mcgltf.workstation.WorkstationCoordinates;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class WorkstationRequestPolicyTest {
    private final BlockPos station = new BlockPos(10, 64, 10);

    @Test
    void rejectsWrongMenuPosition() {
        WorkstationRequestPolicy.Validation validation =
                WorkstationRequestPolicy.validateMenuIdentity(
                        new BlockPos(1, 2, 3), station);
        assertFalse(validation.accepted());
        assertEquals("mcgltf.error.workstation.wrong_station", validation.reasonKey());
    }

    @Test
    void rejectsMissingStation() {
        WorkstationRequestPolicy.Validation validation =
                WorkstationRequestPolicy.validateStationPresent(false);
        assertFalse(validation.accepted());
        assertEquals("mcgltf.error.workstation.missing_station", validation.reasonKey());
    }

    @Test
    void rejectsOutOfRangeY() {
        WorkstationCoordinates coordinates = new WorkstationCoordinates(
                new BlockPos(0, -70, 0), new BlockPos(10, 10, 10));
        WorkstationRequestPolicy.Validation validation =
                WorkstationRequestPolicy.validateCoordinates(coordinates, -64, 319);
        assertFalse(validation.accepted());
        assertEquals("mcgltf.error.workstation.out_of_bounds", validation.reasonKey());
    }

    @Test
    void rejectsUnsafeExportName() {
        assertFalse(WorkstationRequestPolicy.validateExportName("..").accepted());
        assertFalse(WorkstationRequestPolicy.validateExportName("x".repeat(65)).accepted());
        assertFalse(WorkstationRequestPolicy.validateExportName(null).accepted());
    }

    @Test
    void localSingleplayerBypassesExportPermission() {
        assertTrue(WorkstationRequestPolicy
                .validateExportPermission(true, false).accepted());
    }

    @Test
    void dedicatedServerRequiresLevelTwoCommandPermission() {
        WorkstationRequestPolicy.Validation denied = WorkstationRequestPolicy
                .validateExportPermission(false, false);
        assertFalse(denied.accepted());
        assertEquals("mcgltf.error.workstation.no_export_permission", denied.reasonKey());
        assertTrue(WorkstationRequestPolicy
                .validateExportPermission(false, true).accepted());
    }

    @Test
    void acceptsValidRequest() {
        assertTrue(WorkstationRequestPolicy.validateMenuIdentity(station, station).accepted());
        assertTrue(WorkstationRequestPolicy.validateStationPresent(true).accepted());
        WorkstationCoordinates coordinates = new WorkstationCoordinates(
                new BlockPos(0, -64, 0), new BlockPos(511, 319, 511));
        assertTrue(WorkstationRequestPolicy.validateCoordinates(coordinates, -64, 319).accepted());
        assertTrue(WorkstationRequestPolicy.validateExportName("flower_factory").accepted());
    }
}
