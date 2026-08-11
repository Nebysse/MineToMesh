package com.nebysse.minetomesh.testmod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class GpuOnlyBlockEntity extends BlockEntity {
    public GpuOnlyBlockEntity(BlockPos position, BlockState state) {
        super(TestContent.GPU_ONLY_BLOCK_ENTITY.get(), position, state);
    }
}
