package com.onecuber.mcgltf.client.wand;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.onecuber.mcgltf.world.Selection;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class SelectionOverlayRenderer {
    private static final int FACE_ALPHA = 60;
    private static final int FACE_RED = 237;
    private static final int FACE_GREEN = 116;
    private static final int FACE_BLUE = 28;
    private static final int EDGE_ALPHA = 255;
    private static final int EDGE_RED = 52;
    private static final int EDGE_GREEN = 136;
    private static final int EDGE_BLUE = 216;

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
        Optional<Selection> resolved = source.resolve(
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
            AABB box = box(resolved.orElseThrow());
            drawFaces(box, pose, buffers);
            drawEdges(box, pose, buffers);
            buffers.endBatch();
        } finally {
            pose.popPose();
        }
    }

    private static void drawFaces(
            AABB box, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.debugQuads());
        double[][] corners = corners(box);
        int[][] faces = {
                {0, 1, 2, 3}, {4, 5, 6, 7}, {0, 1, 5, 4},
                {2, 3, 7, 6}, {1, 2, 6, 5}, {0, 3, 7, 4}
        };
        for (int[] face : faces) {
            quad(vertices, pose, corners[face[0]], corners[face[1]],
                    corners[face[2]], corners[face[3]]);
        }
    }

    private static void drawEdges(
            AABB box, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.lines());
        double[][] corners = corners(box);
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] edge : edges) {
            line(vertices, pose, corners[edge[0]], corners[edge[1]]);
        }
    }

    private static AABB box(Selection selection) {
        return new AABB(
                selection.min().x(), selection.min().y(), selection.min().z(),
                selection.max().x() + 1,
                selection.max().y() + 1,
                selection.max().z() + 1);
    }

    private static double[][] corners(AABB box) {
        return new double[][]{
                {box.minX, box.minY, box.minZ},
                {box.maxX, box.minY, box.minZ},
                {box.maxX, box.minY, box.maxZ},
                {box.minX, box.minY, box.maxZ},
                {box.minX, box.maxY, box.minZ},
                {box.maxX, box.maxY, box.minZ},
                {box.maxX, box.maxY, box.maxZ},
                {box.minX, box.maxY, box.maxZ}
        };
    }

    private static void quad(
            VertexConsumer vertices,
            PoseStack pose,
            double[] a, double[] b, double[] c, double[] d) {
        PoseStack.Pose last = pose.last();
        vertices.addVertex(last, (float) a[0], (float) a[1], (float) a[2])
                .setColor(FACE_RED, FACE_GREEN, FACE_BLUE, FACE_ALPHA);
        vertices.addVertex(last, (float) b[0], (float) b[1], (float) b[2])
                .setColor(FACE_RED, FACE_GREEN, FACE_BLUE, FACE_ALPHA);
        vertices.addVertex(last, (float) c[0], (float) c[1], (float) c[2])
                .setColor(FACE_RED, FACE_GREEN, FACE_BLUE, FACE_ALPHA);
        vertices.addVertex(last, (float) d[0], (float) d[1], (float) d[2])
                .setColor(FACE_RED, FACE_GREEN, FACE_BLUE, FACE_ALPHA);
    }

    private static void line(
            VertexConsumer vertices, PoseStack pose, double[] a, double[] b) {
        PoseStack.Pose last = pose.last();
        vertices.addVertex(last, (float) a[0], (float) a[1], (float) a[2])
                .setColor(EDGE_RED, EDGE_GREEN, EDGE_BLUE, EDGE_ALPHA)
                .setNormal(last, 0.0F, 1.0F, 0.0F);
        vertices.addVertex(last, (float) b[0], (float) b[1], (float) b[2])
                .setColor(EDGE_RED, EDGE_GREEN, EDGE_BLUE, EDGE_ALPHA)
                .setNormal(last, 0.0F, 1.0F, 0.0F);
    }
}
