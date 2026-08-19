package com.nebysse.minetomesh.testmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nebysse.minetomesh.testmod.TestRenderedBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class TestBlockEntityRenderer implements BlockEntityRenderer<TestRenderedBlockEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace(
            "textures/block/diamond_block.png");

    public TestBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            TestRenderedBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay) {
        TestEntityRenderer.emitCube(
                poseStack,
                buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)),
                0, 255, 255, 255,
                packedLight);
    }
}
