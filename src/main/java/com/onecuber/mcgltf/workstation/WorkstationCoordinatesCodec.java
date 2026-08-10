package com.onecuber.mcgltf.workstation;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public final class WorkstationCoordinatesCodec {
    private static final String FIRST = "First";
    private static final String SECOND = "Second";

    private WorkstationCoordinatesCodec() {
    }

    public static void save(CompoundTag tag, WorkstationCoordinates value) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(value, "value");
        tag.putIntArray(FIRST, coordinates(value.first()));
        tag.putIntArray(SECOND, coordinates(value.second()));
    }

    public static WorkstationCoordinates load(CompoundTag tag, BlockPos fallback) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(fallback, "fallback");
        int[] first = tag.getIntArray(FIRST);
        int[] second = tag.getIntArray(SECOND);
        return first.length == 3 && second.length == 3
                ? new WorkstationCoordinates(pos(first), pos(second))
                : WorkstationCoordinates.at(fallback);
    }

    private static int[] coordinates(BlockPos position) {
        return new int[]{position.getX(), position.getY(), position.getZ()};
    }

    private static BlockPos pos(int[] values) {
        return new BlockPos(values[0], values[1], values[2]);
    }
}
