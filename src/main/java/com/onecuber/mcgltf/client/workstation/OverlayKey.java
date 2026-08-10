package com.onecuber.mcgltf.client.workstation;

import java.util.Objects;
import net.minecraft.core.BlockPos;

public record OverlayKey(String dimension, BlockPos stationPos) {
    public OverlayKey {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(stationPos, "stationPos");
    }
}
