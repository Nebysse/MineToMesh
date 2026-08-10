package com.onecuber.mcgltf.world;

import java.util.Objects;
import java.util.Optional;

public final class SelectionStore {
    private BlockPoint pos1;
    private BlockPoint pos2;

    public void setPos1(BlockPoint point) {
        pos1 = Objects.requireNonNull(point, "point");
    }

    public void setPos2(BlockPoint point) {
        pos2 = Objects.requireNonNull(point, "point");
    }

    public Optional<BlockPoint> pos1() {
        return Optional.ofNullable(pos1);
    }

    public Optional<BlockPoint> pos2() {
        return Optional.ofNullable(pos2);
    }

    public Optional<Selection> selection() {
        if (pos1 == null || pos2 == null) {
            return Optional.empty();
        }
        return Optional.of(Selection.of(pos1, pos2));
    }

    public void clear() {
        pos1 = null;
        pos2 = null;
    }

    public boolean clearIfDimensionChanged(String activeDimension) {
        Objects.requireNonNull(activeDimension, "activeDimension");
        String storedDimension = pos1 != null
                ? pos1.dimension()
                : pos2 != null ? pos2.dimension() : null;
        if (storedDimension != null && !storedDimension.equals(activeDimension)) {
            clear();
            return true;
        }
        return false;
    }
}
