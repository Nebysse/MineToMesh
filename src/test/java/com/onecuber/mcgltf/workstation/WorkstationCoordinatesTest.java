package com.onecuber.mcgltf.workstation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.onecuber.mcgltf.world.Selection;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class WorkstationCoordinatesTest {
    @Test
    void updatesOneEndpointAxisWithoutMutatingOtherValues() {
        WorkstationCoordinates source = new WorkstationCoordinates(
                new BlockPos(1, 2, 3), new BlockPos(4, 5, 6));
        WorkstationCoordinates changed = source.with(Endpoint.SECOND, Axis.Y, 99);
        assertEquals(new BlockPos(1, 2, 3), changed.first());
        assertEquals(new BlockPos(4, 99, 6), changed.second());
    }

    @Test
    void createsNormalizedInclusiveSelection() {
        Selection selection = new WorkstationCoordinates(
                new BlockPos(12, 82, 146), new BlockPos(-24, 64, 108))
                .toSelection("minecraft:overworld");
        assertEquals(37L, selection.sizeX());
        assertEquals(19L, selection.sizeY());
        assertEquals(39L, selection.sizeZ());
        assertEquals(27_417L, selection.volume());
    }

    @Test
    void doesNotImposeAWorkstationVolumeLimit() {
        Selection selection = new WorkstationCoordinates(
                new BlockPos(0, -64, 0), new BlockPos(511, 319, 511))
                .toSelection("minecraft:overworld");
        assertEquals(100_663_296L, selection.volume());
    }
}
