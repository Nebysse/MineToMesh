package com.nebysse.minetomesh.testmod.client;

import com.nebysse.minetomesh.testmod.TestModelDataBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 1.21.10 版 ModelData 夹具：方块实体状态不再经 ModelData 传递，
 * 模型在 collectParts 里直接从 level 读取方块实体。
 * 保留双渲染层语义：几何固定走 CUTOUT，另挂一个空的 TRANSLUCENT 部件。
 */
public final class TestBlockStateModel implements BlockStateModel {
    private final BlockStateModel originalModel;

    public TestBlockStateModel(BlockStateModel originalModel) {
        this.originalModel = originalModel;
    }

    @Deprecated
    @Override
    public void collectParts(RandomSource random, List<BlockModelPart> parts) {
        originalModel.collectParts(random, parts);
    }

    @Override
    public void collectParts(
            BlockAndTintGetter level,
            BlockPos position,
            BlockState state,
            RandomSource random,
            List<BlockModelPart> parts) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof TestModelDataBlockEntity testBlockEntity)
                || testBlockEntity.phase() != TestModelDataBlockEntity.PHASE) {
            throw new IllegalStateException("Exporter did not provide PHASE=1 block entity context");
        }
        List<BlockModelPart> original = new ArrayList<>();
        originalModel.collectParts(level, position, state, random, original);
        for (BlockModelPart part : original) {
            parts.add(new CutoutPart(part));
        }
        parts.add(new EmptyTranslucentPart());
    }

    @Deprecated
    @Override
    public TextureAtlasSprite particleIcon() {
        return originalModel.particleIcon();
    }

    private record CutoutPart(BlockModelPart original) implements BlockModelPart {
        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return original.getQuads(direction);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return original.useAmbientOcclusion();
        }

        @Override
        public TextureAtlasSprite particleIcon() {
            return original.particleIcon();
        }

        @Override
        public ChunkSectionLayer getRenderType(BlockState state) {
            return ChunkSectionLayer.CUTOUT;
        }
    }

    private static final class EmptyTranslucentPart implements BlockModelPart {
        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return List.of();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public TextureAtlasSprite particleIcon() {
            return null;
        }

        @Override
        public ChunkSectionLayer getRenderType(BlockState state) {
            return ChunkSectionLayer.TRANSLUCENT;
        }
    }
}
