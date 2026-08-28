package com.nebysse.minetomesh.testmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nebysse.minetomesh.testmod.GpuOnlyBlockEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;

public final class GpuOnlyBlockEntityRenderer
        implements BlockEntityRenderer<GpuOnlyBlockEntity, BlockEntityRenderState> {
    public GpuOnlyBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public BlockEntityRenderState createRenderState() {
        return new BlockEntityRenderState();
    }

    @Override
    public void submit(
            BlockEntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (!TestBackendAdapter.captureFallback) {
            return;
        }
        collector.submitCustomGeometry(poseStack, RenderType.solid(),
                (pose, consumer) -> TestEntityRenderer.emitCube(
                        pose, consumer, 255, 255, 255, 255, state.lightCoords));
    }
}
