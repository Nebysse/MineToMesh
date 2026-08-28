package com.nebysse.minetomesh.testmod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class TestModelDataBlockEntity extends BlockEntity {
    // 1.21.10 移除了 ModelData/ModelProperty；方块实体状态改由模型在 collectParts 中直接读取。
    public static final int PHASE = 1;

    public TestModelDataBlockEntity(BlockPos position, BlockState state) {
        super(TestContent.MODEL_DATA_BLOCK_ENTITY.get(), position, state);
    }

    public int phase() {
        return PHASE;
    }
}
