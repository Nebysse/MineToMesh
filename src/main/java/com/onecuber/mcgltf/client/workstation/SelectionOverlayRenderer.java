package com.onecuber.mcgltf.client.workstation;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.onecuber.mcgltf.workstation.ExportWorkstationBlockEntity;
import com.onecuber.mcgltf.workstation.WorkstationCoordinates;
import com.onecuber.mcgltf.world.Selection;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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

    private final SelectionOverlayState state;

    public SelectionOverlayRenderer(SelectionOverlayState state) {
        this.state = state;
    }

    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        String dimension = level.dimension().location().toString();
        List<Map.Entry<OverlayKey, WorkstationCoordinates>> active =
                state.activeInDimension(dimension);
        if (active.isEmpty()) {
            return;
        }

        PoseStack pose = event.getPoseStack();
        Vec3 cameraPosition = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        MultiBufferSource.BufferSource buffers =
                MultiBufferSource.immediate(new ByteBufferBuilder(1536));
        try {
            drawFaces(active, level, dimension, pose, buffers);
            drawEdges(active, level, dimension, pose, buffers);
            buffers.endBatch();
        } finally {
            pose.popPose();
        }
    }

    private void drawFaces(
            List<Map.Entry<OverlayKey, WorkstationCoordinates>> active,
            ClientLevel level,
            String dimension,
            PoseStack pose,
            MultiBufferSource buffers) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.debugQuads());
        for (Map.Entry<OverlayKey, WorkstationCoordinates> entry : active) {
            AABB box = box(entry, level, dimension);
            if (box == null) {
                continue;
            }
            double minX = box.minX;
            double minY = box.minY;
            double minZ = box.minZ;
            double maxX = box.maxX;
            double maxY = box.maxY;
            double maxZ = box.maxZ;
            double[][] corners = {
                    {minX, minY, minZ}, {maxX, minY, minZ}, {maxX, minY, maxZ}, {minX, minY, maxZ},
                    {minX, maxY, minZ}, {maxX, maxY, minZ}, {maxX, maxY, maxZ}, {minX, maxY, maxZ}
            };
            int[][] faces = {
                    {0, 1, 2, 3}, {4, 5, 6, 7}, {0, 1, 5, 4},
                    {2, 3, 7, 6}, {1, 2, 6, 5}, {0, 3, 7, 4}
            };
            for (int[] face : faces) {
                quad(vertices, pose, corners[face[0]], corners[face[1]],
                        corners[face[2]], corners[face[3]]);
            }
        }
    }

    private void drawEdges(
            List<Map.Entry<OverlayKey, WorkstationCoordinates>> active,
            ClientLevel level,
            String dimension,
            PoseStack pose,
            MultiBufferSource buffers) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.lines());
        for (Map.Entry<OverlayKey, WorkstationCoordinates> entry : active) {
            AABB box = box(entry, level, dimension);
            if (box == null) {
                continue;
            }
            double minX = box.minX;
            double minY = box.minY;
            double minZ = box.minZ;
            double maxX = box.maxX;
            double maxY = box.maxY;
            double maxZ = box.maxZ;
            double[][] corners = {
                    {minX, minY, minZ}, {maxX, minY, minZ}, {maxX, minY, maxZ}, {minX, minY, maxZ},
                    {minX, maxY, minZ}, {maxX, maxY, minZ}, {maxX, maxY, maxZ}, {minX, maxY, maxZ}
            };
            int[][] edges = {
                    {0, 1}, {1, 2}, {2, 3}, {3, 0},
                    {4, 5}, {5, 6}, {6, 7}, {7, 4},
                    {0, 4}, {1, 5}, {2, 6}, {3, 7}
            };
            for (int[] edge : edges) {
                double[] a = corners[edge[0]];
                double[] b = corners[edge[1]];
                line(vertices, pose, a, b);
            }
        }
    }

    private AABB box(
            Map.Entry<OverlayKey, WorkstationCoordinates> entry,
            ClientLevel level,
            String dimension) {
        OverlayKey key = entry.getKey();
        if (!(level.getBlockEntity(key.stationPos())
                instanceof ExportWorkstationBlockEntity blockEntity)) {
            return null;
        }
        WorkstationCoordinates coordinates = entry.getValue();
        WorkstationCoordinates synchronizedCoordinates = blockEntity.coordinates();
        if (!synchronizedCoordinates.equals(coordinates)) {
            coordinates = synchronizedCoordinates;
            state.refresh(key, synchronizedCoordinates);
        }
        Selection selection = coordinates.toSelection(dimension);
        return new AABB(
                selection.min().x(), selection.min().y(), selection.min().z(),
                selection.max().x() + 1,
                selection.max().y() + 1,
                selection.max().z() + 1);
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
            VertexConsumer vertices,
            PoseStack pose,
            double[] a, double[] b) {
        PoseStack.Pose last = pose.last();
        vertices.addVertex(last, (float) a[0], (float) a[1], (float) a[2])
                .setColor(EDGE_RED, EDGE_GREEN, EDGE_BLUE, EDGE_ALPHA)
                .setNormal(last, 0.0F, 1.0F, 0.0F);
        vertices.addVertex(last, (float) b[0], (float) b[1], (float) b[2])
                .setColor(EDGE_RED, EDGE_GREEN, EDGE_BLUE, EDGE_ALPHA)
                .setNormal(last, 0.0F, 1.0F, 0.0F);
    }
}
