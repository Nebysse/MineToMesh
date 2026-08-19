package com.nebysse.minetomesh.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class WandAirTargetTest {
    @Test
    void selectsTheBlockContainingThePointExactlyTwoMetersAhead() {
        assertEquals(new BlockPos(10, 65, 12), WandAirTarget.twoBlocksAhead(
                new Vec3(10.5, 65.62, 10.5), new Vec3(0.0, 0.0, 1.0)));
        assertEquals(new BlockPos(8, 65, 10), WandAirTarget.twoBlocksAhead(
                new Vec3(10.5, 65.62, 10.5), new Vec3(-4.0, 0.0, 0.0)));
    }

    @Test
    void rejectsAZeroLengthLookVector() {
        assertThrows(IllegalArgumentException.class, () ->
                WandAirTarget.twoBlocksAhead(Vec3.ZERO, Vec3.ZERO));
    }
}
