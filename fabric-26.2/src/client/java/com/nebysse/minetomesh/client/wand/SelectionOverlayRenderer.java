package com.nebysse.minetomesh.client.wand;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nebysse.minetomesh.client.selection.LockedSelectionService;
import com.nebysse.minetomesh.world.Selection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SelectionOverlayRenderer {
    private static final int FACE_RED = 237;
    private static final int FACE_GREEN = 116;
    private static final int FACE_BLUE = 28;
    private static final int FACE_ALPHA = 96;
    private static final int ENDPOINT_ALPHA = 56;
    private static final int POS1_RED = 156;
    private static final int POS1_GREEN = 160;
    private static final int POS1_BLUE = 164;
    private static final int POS2_RED = 52;
    private static final int POS2_GREEN = 136;
    private static final int POS2_BLUE = 216;
    private static final Identifier FORCEFIELD = Identifier.withDefaultNamespace(
            "textures/misc/forcefield.png");

    private final HeldWandOverlaySource heldSource;
    private final LockedSelectionService lockedService;

    public SelectionOverlayRenderer(
            HeldWandOverlaySource heldSource,
            LockedSelectionService lockedService) {
        this.heldSource = Objects.requireNonNull(heldSource, "heldSource");
        this.lockedService = Objects.requireNonNull(lockedService, "lockedService");
    }

    public void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(this::onCollectSubmits);
    }

    private void onCollectSubmits(LevelRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return;
        }
        Identifier dimension = level.dimension().identifier();
        Optional<HeldWandOverlaySource.Snapshot> held = heldSource.resolveSnapshot(
                player.getMainHandItem(), player.getOffhandItem(), dimension);
        Optional<HeldWandOverlaySource.Snapshot> locked = lockedService.resolve(dimension);
        List<HeldWandOverlaySource.Snapshot> snapshots =
                OverlaySnapshotPolicy.merge(held, locked);
        if (snapshots.isEmpty()) {
            return;
        }

        PoseStack poseStack = context.poseStack();
        Vec3 cameraPosition = context.levelState().cameraRenderState.pos;
        poseStack.pushPose();
        poseStack.translate(
                -cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        try {
            for (HeldWandOverlaySource.Snapshot snapshot : snapshots) {
                snapshot.selection().ifPresent(selection ->
                        submitSelectionShell(
                                box(selection), poseStack, context.submitNodeCollector()));
                snapshot.pos1().ifPresent(position -> submitEndpoint(
                        endpointBox(position), poseStack, context.submitNodeCollector(),
                        POS1_RED, POS1_GREEN, POS1_BLUE));
                snapshot.pos2().ifPresent(position -> submitEndpoint(
                        endpointBox(position), poseStack, context.submitNodeCollector(),
                        POS2_RED, POS2_GREEN, POS2_BLUE));
            }
        } finally {
            poseStack.popPose();
        }
    }

    private static void submitSelectionShell(
            AABB box, PoseStack poseStack, SubmitNodeCollector collector) {
        double[][] corners = corners(box);
        collector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityTranslucent(FORCEFIELD, false),
                (pose, vertices) -> {
                    worldQuad(vertices, pose, corners[0], corners[1], corners[2], corners[3]);
                    worldQuad(vertices, pose, corners[4], corners[5], corners[6], corners[7]);
                    worldQuad(vertices, pose, corners[0], corners[1], corners[5], corners[4]);
                    worldQuad(vertices, pose, corners[2], corners[3], corners[7], corners[6]);
                    worldQuad(vertices, pose, corners[1], corners[2], corners[6], corners[5]);
                    worldQuad(vertices, pose, corners[0], corners[3], corners[7], corners[4]);
                });
    }

    private static void worldQuad(
            VertexConsumer vertices, PoseStack.Pose pose,
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
        throw new IllegalArgumentException(
                "A shell face edge must span one world axis");
    }

    private static void worldVertex(
            VertexConsumer vertices, PoseStack.Pose pose,
            double[] point, int uAxis, int vAxis) {
        vertices.addVertex(
                        pose, (float) point[0], (float) point[1], (float) point[2])
                .setColor(FACE_RED, FACE_GREEN, FACE_BLUE, FACE_ALPHA)
                .setUv(worldUv(point[uAxis]), worldUv(point[vAxis]))
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0x00F000F0)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static float worldUv(double coordinate) {
        return (float) coordinate;
    }

    private static void submitEndpoint(
            AABB box, PoseStack poseStack, SubmitNodeCollector collector,
            int red, int green, int blue) {
        double[][] corners = corners(box);
        int[][] faces = {
                {0, 1, 2, 3}, {4, 5, 6, 7}, {0, 1, 5, 4},
                {2, 3, 7, 6}, {1, 2, 6, 5}, {0, 3, 7, 4}
        };
        collector.submitCustomGeometry(
                poseStack, RenderTypes.debugQuads(), (pose, vertices) -> {
                    for (int[] face : faces) {
                        coloredQuad(vertices, pose,
                                corners[face[0]], corners[face[1]],
                                corners[face[2]], corners[face[3]],
                                red, green, blue, ENDPOINT_ALPHA);
                    }
                });
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        collector.submitCustomGeometry(
                poseStack, RenderTypes.lines(), (pose, vertices) -> {
                    for (int[] edge : edges) {
                        line(vertices, pose,
                                corners[edge[0]], corners[edge[1]],
                                red, green, blue);
                    }
                });
    }

    private static AABB endpointBox(BlockPos position) {
        return new AABB(position).inflate(0.002D);
    }

    private static AABB box(Selection selection) {
        return new AABB(
                selection.min().x(), selection.min().y(), selection.min().z(),
                selection.max().x() + 1, selection.max().y() + 1,
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

    private static void coloredQuad(
            VertexConsumer vertices, PoseStack.Pose pose,
            double[] a, double[] b, double[] c, double[] d,
            int red, int green, int blue, int alpha) {
        coloredVertex(vertices, pose, a, red, green, blue, alpha);
        coloredVertex(vertices, pose, b, red, green, blue, alpha);
        coloredVertex(vertices, pose, c, red, green, blue, alpha);
        coloredVertex(vertices, pose, d, red, green, blue, alpha);
    }

    private static void coloredVertex(
            VertexConsumer vertices, PoseStack.Pose pose, double[] point,
            int red, int green, int blue, int alpha) {
        vertices.addVertex(
                        pose, (float) point[0], (float) point[1], (float) point[2])
                .setColor(red, green, blue, alpha);
    }

    private static void line(
            VertexConsumer vertices, PoseStack.Pose pose,
            double[] a, double[] b, int red, int green, int blue) {
        vertices.addVertex(pose, (float) a[0], (float) a[1], (float) a[2])
                .setColor(red, green, blue, 255)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
        vertices.addVertex(pose, (float) b[0], (float) b[1], (float) b[2])
                .setColor(red, green, blue, 255)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
