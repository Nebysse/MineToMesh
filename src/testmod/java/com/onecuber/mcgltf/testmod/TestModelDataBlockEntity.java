package com.onecuber.mcgltf.testmod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public final class TestModelDataBlockEntity extends BlockEntity {
    public static final ModelProperty<Integer> PHASE = new ModelProperty<>();

    public TestModelDataBlockEntity(BlockPos position, BlockState state) {
        super(TestContent.MODEL_DATA_BLOCK_ENTITY.get(), position, state);
    }

    @Override
    public ModelData getModelData() {
        return ModelData.of(PHASE, 1);
    }
}
