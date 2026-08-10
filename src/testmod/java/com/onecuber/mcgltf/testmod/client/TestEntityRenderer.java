package com.onecuber.mcgltf.testmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.onecuber.mcgltf.testmod.TestEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class TestEntityRenderer extends EntityRenderer<TestEntity> {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "mcgltf_test", "runtime/gpu_only");

    public TestEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(
            TestEntity entity,
            float yaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight) {
        emitCube(poseStack, buffers.getBuffer(RenderType.entityCutout(TEXTURE)),
                255, 255, 255, 255, packedLight);
        super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(TestEntity entity) {
        return TEXTURE;
    }

    public static void emitCube(
            PoseStack poseStack,
            VertexConsumer consumer,
            int red,
            int green,
            int blue,
            int alpha,
            int packedLight) {
        face(poseStack, consumer, red, green, blue, alpha, packedLight,
                0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, -1);
        face(poseStack, consumer, red, green, blue, alpha, packedLight,
                0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0, 1);
        face(poseStack, consumer, red, green, blue, alpha, packedLight,
                0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, -1, 0, 0);
        face(poseStack, consumer, red, green, blue, alpha, packedLight,
                1, 0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0);
        face(poseStack, consumer, red, green, blue, alpha, packedLight,
                0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0, 0, -1, 0);
        face(poseStack, consumer, red, green, blue, alpha, packedLight,
                0, 1, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1, 0, 1, 0);
    }

    private static void face(
            PoseStack poseStack,
            VertexConsumer consumer,
            int red,
            int green,
            int blue,
            int alpha,
            int packedLight,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float nx, float ny, float nz) {
        vertex(poseStack, consumer, x0, y0, z0, 0, 0,
                red, green, blue, alpha, packedLight, nx, ny, nz);
        vertex(poseStack, consumer, x1, y1, z1, 1, 0,
                red, green, blue, alpha, packedLight, nx, ny, nz);
        vertex(poseStack, consumer, x2, y2, z2, 1, 1,
                red, green, blue, alpha, packedLight, nx, ny, nz);
        vertex(poseStack, consumer, x3, y3, z3, 0, 1,
                red, green, blue, alpha, packedLight, nx, ny, nz);
    }

    private static void vertex(
            PoseStack poseStack,
            VertexConsumer consumer,
            float x, float y, float z,
            float u, float v,
            int red, int green, int blue, int alpha,
            int packedLight,
            float nx, float ny, float nz) {
        PoseStack.Pose pose = poseStack.last();
        consumer.addVertex(pose, x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, nx, ny, nz);
    }
}
