package com.nebysse.minetomesh.testmod.client;

import com.nebysse.minetomesh.testmod.TestModelDataBlockEntity;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

public final class TestBakedModel extends BakedModelWrapper<BakedModel> {
    public TestBakedModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public ModelData getModelData(
            BlockAndTintGetter level,
            BlockPos position,
            BlockState state,
            ModelData modelData) {
        return modelData;
    }

    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            RandomSource random,
            ModelData modelData,
            @Nullable RenderType renderType) {
        if (!Integer.valueOf(1).equals(modelData.get(TestModelDataBlockEntity.PHASE))) {
            throw new IllegalStateException("Exporter did not pass PHASE=1 ModelData");
        }
        if (renderType == RenderType.cutout()) {
            return originalModel.getQuads(state, side, random, modelData, renderType);
        }
        return List.of();
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(
            BlockState state,
            RandomSource random,
            ModelData modelData) {
        return ChunkRenderTypeSet.of(RenderType.cutout(), RenderType.translucent());
    }
}
