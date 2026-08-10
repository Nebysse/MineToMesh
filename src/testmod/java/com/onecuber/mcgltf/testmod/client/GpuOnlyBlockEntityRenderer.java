package com.onecuber.mcgltf.testmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.onecuber.mcgltf.testmod.GpuOnlyBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public final class GpuOnlyBlockEntityRenderer implements BlockEntityRenderer<GpuOnlyBlockEntity> {
    public GpuOnlyBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            GpuOnlyBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay) {
        if (!TestBackendAdapter.captureFallback) {
            return;
        }
        TestEntityRenderer.emitCube(
                poseStack,
                buffers.getBuffer(RenderType.solid()),
                255, 255, 255, 255,
                packedLight);
    }
}
