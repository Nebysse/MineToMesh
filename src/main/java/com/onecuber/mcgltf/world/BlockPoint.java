package com.onecuber.mcgltf.world;

import java.util.Objects;

public record BlockPoint(String dimension, int x, int y, int z) {
    public BlockPoint {
        Objects.requireNonNull(dimension, "dimension");
    }
}
