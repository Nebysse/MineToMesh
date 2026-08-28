package com.nebysse.minetomesh.client.wand;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nebysse.minetomesh.client.selection.LockedSelectionService;
import com.nebysse.minetomesh.world.Selection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

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

    private final HeldWandOverlaySource heldSource;
    private final LockedSelectionService lockedService;

    public SelectionOverlayRenderer(
            HeldWandOverlaySource heldSource,
            LockedSelectionService lockedService) {
        this.heldSource = Objects.requireNonNull(heldSource, "heldSource");
        this.lockedService = Objects.requireNonNull(lockedService, "lockedService");
    }

    public void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return;
        }
        ResourceLocation dimension = level.dimension().location();
        Optional<HeldWandOverlaySource.Snapshot> held = heldSource.resolveSnapshot(
                player.getMainHandItem(), player.getOffhandItem(), dimension);
        Optional<HeldWandOverlaySource.Snapshot> locked = lockedService.resolve(dimension);
        List<HeldWandOverlaySource.Snapshot> snapshots =
                OverlaySnapshotPolicy.merge(held, locked);
        if (snapshots.isEmpty()) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        Vec3 cameraPosition = event.getLevelRenderState().cameraRenderState.pos;
        pose.pushPose();
        pose.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        try {
            for (HeldWandOverlaySource.Snapshot snapshot : snapshots) {
                snapshot.selection().ifPresent(selection ->
                        drawSelectionShell(box(selection), pose));
                snapshot.pos1().ifPresent(pos -> drawEndpoint(
                        endpointBox(pos), pose,
                        POS1_RED, POS1_GREEN, POS1_BLUE));
                snapshot.pos2().ifPresent(pos -> drawEndpoint(
                        endpointBox(pos), pose,
                        POS2_RED, POS2_GREEN, POS2_BLUE));
            }
        } finally {
            pose.popPose();
        }
    }

    private static void drawSelectionShell(AABB box, PoseStack pose) {
        // 1.21.10 移除 BufferUploader 后，纹理壳按 vanilla 世界边界同款管线绘制：
        // POSITION_TEX 顶点 + RenderPipelines.WORLD_BORDER + 动态 uniform 染色。
        BufferBuilder vertices = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        double[][] c = corners(box);
        worldQuad(vertices, pose, c[0], c[1], c[2], c[3]);
        worldQuad(vertices, pose, c[4], c[5], c[6], c[7]);
        worldQuad(vertices, pose, c[0], c[1], c[5], c[4]);
        worldQuad(vertices, pose, c[2], c[3], c[7], c[6]);
        worldQuad(vertices, pose, c[1], c[2], c[6], c[5]);
        worldQuad(vertices, pose, c[0], c[3], c[7], c[4]);
        MeshData mesh = vertices.build();
        if (mesh == null) {
            return;
        }
        try {
            RenderPipeline pipeline = RenderPipelines.WORLD_BORDER;
            GpuBuffer vertexBuffer = pipeline.getVertexFormat()
                    .uploadImmediateVertexBuffer(mesh.vertexBuffer());
            GpuBuffer indexBuffer;
            VertexFormat.IndexType indexType;
            if (mesh.indexBuffer() == null) {
                RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem
                        .getSequentialBuffer(mesh.drawState().mode());
                indexBuffer = sequential.getBuffer(mesh.drawState().indexCount());
                indexType = sequential.type();
            } else {
                indexBuffer = pipeline.getVertexFormat()
                        .uploadImmediateIndexBuffer(mesh.indexBuffer());
                indexType = mesh.drawState().indexType();
            }

            TextureManager textureManager = Minecraft.getInstance().getTextureManager();
            AbstractTexture texture = textureManager.getTexture(FORCEFIELD);
            texture.setUseMipmaps(false);
            RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
            RenderTarget weatherTarget = Minecraft.getInstance().levelRenderer.getWeatherTarget();
            GpuTextureView colorView;
            GpuTextureView depthView;
            if (weatherTarget != null) {
                colorView = weatherTarget.getColorTextureView();
                depthView = weatherTarget.getDepthTextureView();
            } else {
                colorView = mainTarget.getColorTextureView();
                depthView = mainTarget.getDepthTextureView();
            }

            GpuBufferSlice uniforms = RenderSystem.getDynamicUniforms()
                    .writeTransform(
                            RenderSystem.getModelViewMatrix(),
                            new Vector4f(FACE_RED / 255.0F, FACE_GREEN / 255.0F,
                                    FACE_BLUE / 255.0F, 1.0F),
                            new Vector3f(0.0F, 0.0F, 0.0F),
                            new Matrix4f(),
                            0.0F);

            try (RenderPass renderPass = RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(() -> "minetomesh_selection_shell",
                            colorView, OptionalInt.empty(), depthView, OptionalDouble.empty())) {
                renderPass.setPipeline(pipeline);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("DynamicTransforms", uniforms);
                renderPass.setIndexBuffer(indexBuffer, indexType);
                renderPass.bindSampler("Sampler0", texture.getTextureView());
                renderPass.setVertexBuffer(0, vertexBuffer);
                List<RenderPass.Draw<SelectionOverlayRenderer>> draws = new ArrayList<>();
                draws.add(new RenderPass.Draw<>(
                        0, vertexBuffer, indexBuffer, indexType, 0, 36));
                renderPass.drawMultipleIndexed(
                        draws, null, null, Collections.emptyList(), null);
            }
        } finally {
            mesh.close();
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
            AABB box, PoseStack pose,
            int red, int green, int blue) {
        double[][] corners = corners(box);
        int[][] faces = {
                {0, 1, 2, 3}, {4, 5, 6, 7}, {0, 1, 5, 4},
                {2, 3, 7, 6}, {1, 2, 6, 5}, {0, 3, 7, 4}
        };
        BufferBuilder faceVertices = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int[] face : faces) {
            coloredQuad(faceVertices, pose, corners[face[0]], corners[face[1]],
                    corners[face[2]], corners[face[3]], red, green, blue, ENDPOINT_ALPHA);
        }
        MeshData faceMesh = faceVertices.build();
        if (faceMesh != null) {
            RenderType.debugQuads().draw(faceMesh);
            faceMesh.close();
        }

        BufferBuilder lines = Tesselator.getInstance().begin(
                VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] edge : edges) {
            line(lines, pose, corners[edge[0]], corners[edge[1]], red, green, blue);
        }
        MeshData lineMesh = lines.build();
        if (lineMesh != null) {
            RenderType.lines().draw(lineMesh);
            lineMesh.close();
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

    private static void coloredQuad(BufferBuilder vertices, PoseStack pose,
            double[] a, double[] b, double[] c, double[] d, int red, int green, int blue, int alpha) {
        PoseStack.Pose last = pose.last();
        vertices.addVertex(last, (float) a[0], (float) a[1], (float) a[2]).setColor(red, green, blue, alpha);
        vertices.addVertex(last, (float) b[0], (float) b[1], (float) b[2]).setColor(red, green, blue, alpha);
        vertices.addVertex(last, (float) c[0], (float) c[1], (float) c[2]).setColor(red, green, blue, alpha);
        vertices.addVertex(last, (float) d[0], (float) d[1], (float) d[2]).setColor(red, green, blue, alpha);
    }

    private static void line(BufferBuilder vertices, PoseStack pose, double[] a, double[] b,
            int red, int green, int blue) {
        PoseStack.Pose last = pose.last();
        vertices.addVertex(last, (float) a[0], (float) a[1], (float) a[2])
                .setColor(red, green, blue, 255).setNormal(last, 0.0F, 1.0F, 0.0F);
        vertices.addVertex(last, (float) b[0], (float) b[1], (float) b[2])
                .setColor(red, green, blue, 255).setNormal(last, 0.0F, 1.0F, 0.0F);
    }
}
