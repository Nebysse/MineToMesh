package com.nebysse.minetomesh.testmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nebysse.minetomesh.testmod.TestRenderedBlockEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.ResourceLocation;

public final class TestBlockEntityRenderer
        implements BlockEntityRenderer<TestRenderedBlockEntity, BlockEntityRenderState> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace(
            "textures/block/diamond_block.png");

    public TestBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
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
        collector.submitCustomGeometry(poseStack, RenderType.entityTranslucent(TEXTURE),
                (pose, consumer) -> TestEntityRenderer.emitCube(
                        pose, consumer, 0, 255, 255, 255, state.lightCoords));
    }
}
