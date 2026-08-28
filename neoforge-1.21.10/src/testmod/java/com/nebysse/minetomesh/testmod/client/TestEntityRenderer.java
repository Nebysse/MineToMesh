package com.nebysse.minetomesh.testmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nebysse.minetomesh.testmod.TestEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class TestEntityRenderer extends EntityRenderer<TestEntity, EntityRenderState> {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "minetomesh_test", "runtime/gpu_only");

    public TestEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void submit(
            EntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera) {
        collector.submitCustomGeometry(poseStack, RenderType.entityCutout(TEXTURE),
                (pose, consumer) -> emitCube(
                        pose, consumer, 255, 255, 255, 255, state.lightCoords));
        super.submit(state, poseStack, collector, camera);
    }

    public static void emitCube(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int red,
            int green,
            int blue,
            int alpha,
            int packedLight) {
        face(pose, consumer, red, green, blue, alpha, packedLight,
                0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, -1);
        face(pose, consumer, red, green, blue, alpha, packedLight,
                0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0, 1);
        face(pose, consumer, red, green, blue, alpha, packedLight,
                0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, -1, 0, 0);
        face(pose, consumer, red, green, blue, alpha, packedLight,
                1, 0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0);
        face(pose, consumer, red, green, blue, alpha, packedLight,
                0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0, 0, -1, 0);
        face(pose, consumer, red, green, blue, alpha, packedLight,
                0, 1, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1, 0, 1, 0);
    }

    private static void face(
            PoseStack.Pose pose,
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
        vertex(pose, consumer, x0, y0, z0, 0, 0,
                red, green, blue, alpha, packedLight, nx, ny, nz);
        vertex(pose, consumer, x1, y1, z1, 1, 0,
                red, green, blue, alpha, packedLight, nx, ny, nz);
        vertex(pose, consumer, x2, y2, z2, 1, 1,
                red, green, blue, alpha, packedLight, nx, ny, nz);
        vertex(pose, consumer, x3, y3, z3, 0, 1,
                red, green, blue, alpha, packedLight, nx, ny, nz);
    }

    private static void vertex(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            float x, float y, float z,
            float u, float v,
            int red, int green, int blue, int alpha,
            int packedLight,
            float nx, float ny, float nz) {
        consumer.addVertex(pose, x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, nx, ny, nz);
    }
}
