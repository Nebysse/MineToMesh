package com.onecuber.mcgltf.workstation;

import com.onecuber.mcgltf.world.BlockPoint;
import com.onecuber.mcgltf.world.Selection;
import java.util.Objects;
import net.minecraft.core.BlockPos;

public record WorkstationCoordinates(BlockPos first, BlockPos second) {
    public WorkstationCoordinates {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
    }

    public static WorkstationCoordinates at(BlockPos position) {
        Objects.requireNonNull(position, "position");
        return new WorkstationCoordinates(position.immutable(), position.immutable());
    }

    public WorkstationCoordinates with(Endpoint endpoint, Axis axis, int value) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(axis, "axis");
        BlockPos source = endpoint == Endpoint.FIRST ? first : second;
        BlockPos changed = switch (axis) {
            case X -> new BlockPos(value, source.getY(), source.getZ());
            case Y -> new BlockPos(source.getX(), value, source.getZ());
            case Z -> new BlockPos(source.getX(), source.getY(), value);
        };
        return endpoint == Endpoint.FIRST
                ? new WorkstationCoordinates(changed, second)
                : new WorkstationCoordinates(first, changed);
    }

    public WorkstationCoordinates withEndpoint(Endpoint endpoint, BlockPos position) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(position, "position");
        return endpoint == Endpoint.FIRST
                ? new WorkstationCoordinates(position.immutable(), second)
                : new WorkstationCoordinates(first, position.immutable());
    }

    public Selection toSelection(String dimension) {
        Objects.requireNonNull(dimension, "dimension");
        return Selection.of(point(dimension, first), point(dimension, second));
    }

    private static BlockPoint point(String dimension, BlockPos position) {
        return new BlockPoint(dimension, position.getX(), position.getY(), position.getZ());
    }
}
