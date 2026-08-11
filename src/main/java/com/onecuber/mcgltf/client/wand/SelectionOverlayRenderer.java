package com.onecuber.mcgltf.client.wand;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.onecuber.mcgltf.world.Selection;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class SelectionOverlayRenderer {
    private static final int FACE_RED = 237;
    private static final int FACE_GREEN = 116;
    private static final int FACE_BLUE = 28;
    private static final int ENDPOINT_ALPHA = 56;
    private static final int POS1_RED = 156;
    private static final int POS1_GREEN = 160;
    private static final int POS1_BLUE = 164;
    private static final int POS2_RED = 255;
    private static final int POS2_GREEN = 255;
    private static final int POS2_BLUE = 255;
    private static final ResourceLocation FORCEFIELD = ResourceLocation.withDefaultNamespace(
            "textures/misc/forcefield.png");

    private final HeldWandOverlaySource source;

    public SelectionOverlayRenderer(HeldWandOverlaySource source) {
        this.source = source;
    }

    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return;
        }
        Optional<HeldWandOverlaySource.Snapshot> resolved = source.resolveSnapshot(
                player.getMainHandItem(), player.getOffhandItem(),
                level.dimension().location());
        if (resolved.isEmpty()) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        Vec3 cameraPosition = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        MultiBufferSource.BufferSource buffers =
                MultiBufferSource.immediate(new ByteBufferBuilder(1536));
        try {
            HeldWandOverlaySource.Snapshot snapshot = resolved.orElseThrow();
            snapshot.selection().ifPresent(selection ->
                    drawSelectionShell(box(selection), pose));
            snapshot.pos1().ifPresent(pos -> drawEndpoint(
                    endpointBox(pos), pose, buffers, POS1_RED, POS1_GREEN, POS1_BLUE));
            snapshot.pos2().ifPresent(pos -> drawEndpoint(
                    endpointBox(pos), pose, buffers, POS2_RED, POS2_GREEN, POS2_BLUE));
            buffers.endBatch();
        } finally {
            pose.popPose();
        }
    }

    private static void drawSelectionShell(AABB box, PoseStack pose) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderTexture(0, FORCEFIELD);
        RenderSystem.setShaderColor(FACE_RED / 255.0F, FACE_GREEN / 255.0F,
                FACE_BLUE / 255.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        try {
            BufferBuilder vertices = Tesselator.getInstance().begin(
                    VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            double[][] c = corners(box);
            worldQuad(vertices, pose, c[0], c[1], c[2], c[3]);
            worldQuad(vertices, pose, c[4], c[5], c[6], c[7]);
            worldQuad(vertices, pose, c[0], c[1], c[5], c[4]);
            worldQuad(vertices, pose, c[2], c[3], c[7], c[6]);
            worldQuad(vertices, pose, c[1], c[2], c[6], c[5]);
            worldQuad(vertices, pose, c[0], c[3], c[7], c[4]);
            BufferUploader.drawWithShader(vertices.buildOrThrow());
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static void worldQuad(BufferBuilder vertices, PoseStack pose,
            double[] a, double[] b, double[] c, double[] d) {
        int uAxis = varyingAxis(a, b);
        int vAxis = varyingAxis(a, d);
        worldVertex(vertices, pose, a, uAxis, vAxis);
        worldVertex(vertices, pose, b, uAxis, vAxis);
        worldVertex(vertices, pose, c, uAxis, vAxis);
        worldVertex(vertices, pose, d, uAxis, vAxis);
    }

    private static int varyingAxis(double[] first, double[] second) {
        for (int axis = 0; axis < 3; axis++) {
            if (first[axis] != second[axis]) {
                return axis;
            }
        }
        throw new IllegalArgumentException("A shell face edge must span one world axis");
    }

    private static void worldVertex(BufferBuilder vertices, PoseStack pose,
            double[] point, int uAxis, int vAxis) {
        vertices.addVertex(pose.last(), (float) point[0], (float) point[1], (float) point[2])
                .setUv(worldUv(point[uAxis]), worldUv(point[vAxis]));
    }

    private static float worldUv(double coordinate) {
        return (float) coordinate;
    }

    private static void drawEndpoint(
            AABB box, PoseStack pose, MultiBufferSource buffers,
            int red, int green, int blue) {
        double[][] corners = corners(box);
        int[][] faces = {
                {0, 1, 2, 3}, {4, 5, 6, 7}, {0, 1, 5, 4},
                {2, 3, 7, 6}, {1, 2, 6, 5}, {0, 3, 7, 4}
        };
        VertexConsumer faceVertices = buffers.getBuffer(RenderType.debugQuads());
        for (int[] face : faces) {
            coloredQuad(faceVertices, pose, corners[face[0]], corners[face[1]],
                    corners[face[2]], corners[face[3]], red, green, blue, ENDPOINT_ALPHA);
        }
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] edge : edges) {
            line(lines, pose, corners[edge[0]], corners[edge[1]], red, green, blue);
        }
    }

    private static AABB endpointBox(BlockPos position) {
        return new AABB(position).inflate(0.002D);
    }

    private static AABB box(Selection selection) {
        return new AABB(selection.min().x(), selection.min().y(), selection.min().z(),
                selection.max().x() + 1, selection.max().y() + 1, selection.max().z() + 1);
    }

    private static double[][] corners(AABB box) {
        return new double[][]{{box.minX, box.minY, box.minZ}, {box.maxX, box.minY, box.minZ},
                {box.maxX, box.minY, box.maxZ}, {box.minX, box.minY, box.maxZ},
                {box.minX, box.maxY, box.minZ}, {box.maxX, box.maxY, box.minZ},
                {box.maxX, box.maxY, box.maxZ}, {box.minX, box.maxY, box.maxZ}};
    }

    private static void texturedQuad(VertexConsumer vertices, PoseStack pose,
            double[] a, double[] b, double[] c, double[] d) {
        PoseStack.Pose last = pose.last();
        texturedVertex(vertices, last, a, 0.0F, 0.0F);
        texturedVertex(vertices, last, b, 1.0F, 0.0F);
        texturedVertex(vertices, last, c, 1.0F, 1.0F);
        texturedVertex(vertices, last, d, 0.0F, 1.0F);
    }

    private static void texturedVertex(VertexConsumer vertices, PoseStack.Pose pose,
            double[] point, float u, float v) {
        vertices.addVertex(pose, (float) point[0], (float) point[1], (float) point[2])
                .setColor(FACE_RED, FACE_GREEN, FACE_BLUE, 255)
                .setUv(u, v)
                .setUv1(0, 0)
                .setUv2(0, 0)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static void coloredQuad(VertexConsumer vertices, PoseStack pose,
            double[] a, double[] b, double[] c, double[] d, int red, int green, int blue, int alpha) {
        PoseStack.Pose last = pose.last();
        vertices.addVertex(last, (float) a[0], (float) a[1], (float) a[2]).setColor(red, green, blue, alpha);
        vertices.addVertex(last, (float) b[0], (float) b[1], (float) b[2]).setColor(red, green, blue, alpha);
        vertices.addVertex(last, (float) c[0], (float) c[1], (float) c[2]).setColor(red, green, blue, alpha);
        vertices.addVertex(last, (float) d[0], (float) d[1], (float) d[2]).setColor(red, green, blue, alpha);
    }

    private static void line(VertexConsumer vertices, PoseStack pose, double[] a, double[] b,
            int red, int green, int blue) {
        PoseStack.Pose last = pose.last();
        vertices.addVertex(last, (float) a[0], (float) a[1], (float) a[2])
                .setColor(red, green, blue, 255).setNormal(last, 0.0F, 1.0F, 0.0F);
        vertices.addVertex(last, (float) b[0], (float) b[1], (float) b[2])
                .setColor(red, green, blue, 255).setNormal(last, 0.0F, 1.0F, 0.0F);
    }
}
