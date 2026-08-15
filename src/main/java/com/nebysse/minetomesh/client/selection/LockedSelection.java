package com.nebysse.minetomesh.client.selection;

import com.nebysse.minetomesh.client.wand.HeldWandOverlaySource;
import com.nebysse.minetomesh.world.BlockPoint;
import com.nebysse.minetomesh.world.Selection;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record LockedSelection(
        ResourceLocation dimension,
        BlockPos pos1,
        BlockPos pos2) {
    public LockedSelection {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(pos1, "pos1");
        Objects.requireNonNull(pos2, "pos2");
    }

    public Selection toSelection() {
        return Selection.of(
                new BlockPoint(dimension.toString(),
                        pos1.getX(), pos1.getY(), pos1.getZ()),
                new BlockPoint(dimension.toString(),
                        pos2.getX(), pos2.getY(), pos2.getZ()));
    }

    public Optional<HeldWandOverlaySource.Snapshot> snapshot(
            ResourceLocation currentDimension) {
        Objects.requireNonNull(currentDimension, "currentDimension");
        return dimension.equals(currentDimension)
                ? Optional.of(new HeldWandOverlaySource.Snapshot(
                        Optional.of(pos1), Optional.of(pos2),
                        Optional.of(toSelection()), dimension))
                : Optional.empty();
    }
}
