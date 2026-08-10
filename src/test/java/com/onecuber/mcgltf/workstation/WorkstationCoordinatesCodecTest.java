package com.onecuber.mcgltf.workstation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class WorkstationCoordinatesCodecTest {
    @Test
    void roundTripsBothEndpoints() {
        WorkstationCoordinates coordinates = new WorkstationCoordinates(
                new BlockPos(1, 2, 3), new BlockPos(-4, 255, 1000));
        CompoundTag tag = new CompoundTag();
        WorkstationCoordinatesCodec.save(tag, coordinates);
        WorkstationCoordinates loaded = WorkstationCoordinatesCodec.load(
                tag, new BlockPos(0, 0, 0));
        assertEquals(coordinates, loaded);
    }

    @Test
    void fallsBackWhenFieldsMissing() {
        CompoundTag tag = new CompoundTag();
        BlockPos fallback = new BlockPos(7, 8, 9);
        assertEquals(
                WorkstationCoordinates.at(fallback),
                WorkstationCoordinatesCodec.load(tag, fallback));
    }

    @Test
    void fallsBackOnWrongLengthArrays() {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("First", new int[]{1, 2});
        tag.putIntArray("Second", new int[]{3, 4, 5, 6});
        BlockPos fallback = new BlockPos(7, 8, 9);
        assertEquals(
                WorkstationCoordinates.at(fallback),
                WorkstationCoordinatesCodec.load(tag, fallback));
    }
}
