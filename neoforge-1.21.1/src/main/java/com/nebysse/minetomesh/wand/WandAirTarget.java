package com.nebysse.minetomesh.wand;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class WandAirTarget {
    public static final double DISTANCE = 2.0;

    private WandAirTarget() {
    }

    public static BlockPos twoBlocksAhead(Vec3 eyePosition, Vec3 lookDirection) {
        Objects.requireNonNull(eyePosition, "eyePosition");
        Objects.requireNonNull(lookDirection, "lookDirection");
        if (lookDirection.lengthSqr() < 1.0E-12) {
            throw new IllegalArgumentException("lookDirection must not be zero");
        }
        Vec3 target = eyePosition.add(
                lookDirection.normalize().scale(DISTANCE));
        return BlockPos.containing(target);
    }
}
