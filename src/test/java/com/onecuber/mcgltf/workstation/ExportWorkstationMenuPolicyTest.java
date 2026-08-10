package com.onecuber.mcgltf.workstation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ExportWorkstationMenuPolicyTest {
    private final BlockPos station = new BlockPos(10, 64, 10);
    private final Vec3 playerNear = new Vec3(10.5, 64.5, 10.5);
    private final Vec3 playerFar = new Vec3(100, 64, 100);

    @Test
    void acceptsNearbyPlayerWithPresentStation() {
        assertTrue(ExportWorkstationMenu.isValidStation(playerNear, station, true));
    }

    @Test
    void rejectsPlayerBeyondEightBlocks() {
        assertFalse(ExportWorkstationMenu.isValidStation(playerFar, station, true));
    }

    @Test
    void rejectsMissingStation() {
        assertFalse(ExportWorkstationMenu.isValidStation(playerNear, station, false));
    }
}
