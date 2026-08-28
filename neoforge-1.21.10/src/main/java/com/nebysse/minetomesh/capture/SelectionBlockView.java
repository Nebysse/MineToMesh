package com.nebysse.minetomesh.capture;

import com.nebysse.minetomesh.world.Selection;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public final class SelectionBlockView implements BlockAndTintGetter {
    private final ClientLevel delegate;
    private final Selection selection;

    public SelectionBlockView(ClientLevel delegate, Selection selection) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.selection = Objects.requireNonNull(selection, "selection");
    }

    public static boolean shouldDelegate(
            Selection selection,
            int x,
            int y,
            int z,
            ChunkLoaded chunkLoaded) {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(chunkLoaded, "chunkLoaded");
        return selection.contains(x, y, z)
                && chunkLoaded.test(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
    }

    private boolean delegates(BlockPos position) {
        return shouldDelegate(selection,
                position.getX(), position.getY(), position.getZ(), delegate::hasChunk);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos position) {
        return delegates(position) ? delegate.getBlockEntity(position) : null;
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        return delegates(position) ? delegate.getBlockState(position) : Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos position) {
        return delegates(position) ? delegate.getFluidState(position) : Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return 1.0F;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return delegate.getLightEngine();
    }

    @Override
    public int getBlockTint(BlockPos position, ColorResolver resolver) {
        return delegate.getBlockTint(position, resolver);
    }

    @Override
    public int getHeight() {
        return delegate.getHeight();
    }

    @Override
    public int getMinY() {
        return delegate.getMinY();
    }

    @FunctionalInterface
    public interface ChunkLoaded {
        boolean test(int chunkX, int chunkZ);
    }
}
