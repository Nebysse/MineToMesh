package com.nebysse.minetomesh.testmod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class TestRenderedBlockEntity extends BlockEntity {
    public TestRenderedBlockEntity(BlockPos position, BlockState state) {
        super(TestContent.RENDERED_BLOCK_ENTITY.get(), position, state);
    }
}
