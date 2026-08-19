package com.nebysse.minetomesh.capture;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nebysse.minetomesh.backend.RenderBackendRegistry;
import com.nebysse.minetomesh.material.MaterialResolver;
import com.nebysse.minetomesh.scene.BatchCounters;
import com.nebysse.minetomesh.scene.CapturedNode;
import com.nebysse.minetomesh.scene.Diagnostic;
import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.PrimitiveData;
import com.nebysse.minetomesh.world.BlockPoint;
import com.nebysse.minetomesh.world.Selection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class BlockEntityCapture {
    private final Function<RenderTypeDescriptor, MaterialKey> materialResolver;
    private final RendererReplay rendererReplay;

    public BlockEntityCapture() {
        this(MaterialResolver::resolve);
    }

    public BlockEntityCapture(Function<RenderTypeDescriptor, MaterialKey> materialResolver) {
        this(materialResolver, new RendererReplay(RenderBackendRegistry.discover(
                BlockEntityCapture.class.getClassLoader())));
    }

    public BlockEntityCapture(
            Function<RenderTypeDescriptor, MaterialKey> materialResolver,
            RendererReplay rendererReplay) {
        this.materialResolver = Objects.requireNonNull(materialResolver, "materialResolver");
        this.rendererReplay = Objects.requireNonNull(rendererReplay, "rendererReplay");
    }

    public CaptureResult capture(BlockEntity blockEntity, Selection selection) {
        Objects.requireNonNull(blockEntity, "blockEntity");
        Objects.requireNonNull(selection, "selection");
        String registryId = BuiltInRegistries.BLOCK_ENTITY_TYPE
                .getKey(blockEntity.getType()).toString();
        BlockPos position = blockEntity.getBlockPos();
        String objectId = registryId + "/"
                + position.getX() + "," + position.getY() + "," + position.getZ();
        BlockEntityRenderer<BlockEntity> renderer = renderer(blockEntity);
        if (renderer == null) {
            Diagnostic diagnostic = diagnostic(
                    Diagnostic.Severity.WARNING,
                    "BLOCK_ENTITY_RENDERER_MISSING",
                    objectId,
                    position,
                    selection,
                    "",
                    "",
                    "Block entity has no renderer and is represented by its block model");
            return new CaptureResult(
                    Optional.empty(),
                    CaptureState.EMPTY,
                    List.of(diagnostic),
                    blockEntityCounter());
        }

        CapturingMultiBufferSource buffers = new CapturingMultiBufferSource(
                objectId, materialResolver);
        try {
            PoseStack poseStack = blockEntityPose(position, selection);
            RendererReplay.Outcome replay = rendererReplay.run(() -> renderer.render(
                    blockEntity, 0.0F, poseStack, buffers,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY));
            if (!replay.success()) {
                Exception exception = replay.failure().orElseThrow();
                String code = replay.failureStage() == RendererReplay.FailureStage.BACKEND
                        || replay.failureStage() == RendererReplay.FailureStage.RESTORE
                        ? "RENDER_BACKEND_FALLBACK_FAILED"
                        : "BLOCK_ENTITY_CAPTURE_FAILED";
                Diagnostic diagnostic = diagnostic(
                        Diagnostic.Severity.FAILURE,
                        code,
                        objectId,
                        position,
                        selection,
                        renderer.getClass().getName(),
                        exception.getClass().getName(),
                        exception.getMessage() == null ? code : exception.getMessage());
                return new CaptureResult(
                        Optional.empty(), CaptureState.FAILED,
                        List.of(diagnostic), blockEntityCounter());
            }
            CapturingMultiBufferSource.CaptureResult captured = buffers.finishAll();
            List<Diagnostic> captureDiagnostics = new ArrayList<>(captured.diagnostics());
            if (replay.fallbackUsed()) {
                captureDiagnostics.add(diagnostic(
                        Diagnostic.Severity.INFO,
                        "RENDER_BACKEND_FALLBACK_USED",
                        objectId,
                        position,
                        selection,
                        renderer.getClass().getName(),
                        "",
                        replay.adapterId()));
            }
            Map<String, Object> extras = extras(
                    registryId, position, selection, renderer.getClass().getName());
            if (hasGeometry(captured.primitives())) {
                CapturedNode node = new CapturedNode(
                        objectId,
                        CapturedNode.Kind.BLOCK_ENTITY,
                        captured.primitives(),
                        extras);
                return new CaptureResult(
                        Optional.of(node),
                        CaptureState.GEOMETRY,
                        captureDiagnostics,
                        new BatchCounters(0, 0, 0, 1, 0, 0, 0,
                                triangleCount(captured.primitives()), 0));
            }
            List<Diagnostic> diagnostics = new ArrayList<>(captureDiagnostics);
            diagnostics.add(diagnostic(
                    Diagnostic.Severity.INFO,
                    "AUXILIARY_RENDERER_EMPTY",
                    objectId,
                    position,
                    selection,
                    renderer.getClass().getName(),
                    "",
                    "Block entity renderer emitted no exportable vertices"));
            return new CaptureResult(
                    Optional.empty(), CaptureState.EMPTY, diagnostics, blockEntityCounter());
        } catch (Exception exception) {
            Diagnostic diagnostic = diagnostic(
                    Diagnostic.Severity.FAILURE,
                    "BLOCK_ENTITY_CAPTURE_FAILED",
                    objectId,
                    position,
                    selection,
                    renderer.getClass().getName(),
                    exception.getClass().getName(),
                    exception.getMessage() == null
                            ? "Block entity capture failed" : exception.getMessage());
            return new CaptureResult(
                    Optional.empty(),
                    CaptureState.FAILED,
                    List.of(diagnostic),
                    blockEntityCounter());
        }
    }

    private static BatchCounters blockEntityCounter() {
        return new BatchCounters(0, 0, 0, 1, 0, 0, 0, 0, 0);
    }

    private static PoseStack blockEntityPose(BlockPos position, Selection selection) {
        return CaptureCoordinates.translatedPose(CaptureCoordinates.localPosition(
                position.getX(), position.getY(), position.getZ(), selection));
    }

    @SuppressWarnings("unchecked")
    private static BlockEntityRenderer<BlockEntity> renderer(BlockEntity blockEntity) {
        BlockEntityRenderDispatcher dispatcher = Minecraft.getInstance()
                .getBlockEntityRenderDispatcher();
        return (BlockEntityRenderer<BlockEntity>) dispatcher.getRenderer(blockEntity);
    }

    private static Map<String, Object> extras(
            String registryId,
            BlockPos position,
            Selection selection,
            String rendererClass) {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("registryId", registryId);
        extras.put("worldPosition", List.of(
                position.getX(), position.getY(), position.getZ()));
        com.nebysse.minetomesh.scene.Vec3f local = CaptureCoordinates.localPosition(
                position.getX(), position.getY(), position.getZ(), selection);
        extras.put("localPosition", List.of(local.x(), local.y(), local.z()));
        extras.put("rendererClass", rendererClass);
        return extras;
    }

    private static Diagnostic diagnostic(
            Diagnostic.Severity severity,
            String code,
            String objectId,
            BlockPos position,
            Selection selection,
            String rendererClass,
            String exceptionClass,
            String message) {
        return new Diagnostic(
                severity,
                code,
                objectId,
                Optional.of(new BlockPoint(
                        selection.min().dimension(),
                        position.getX(), position.getY(), position.getZ())),
                rendererClass,
                exceptionClass,
                message);
    }

    private static boolean hasGeometry(List<PrimitiveData> primitives) {
        return primitives.stream().anyMatch(primitive -> primitive.indices().length > 0);
    }

    private static long triangleCount(List<PrimitiveData> primitives) {
        long count = 0;
        for (PrimitiveData primitive : primitives) {
            int indexCount = primitive.indices().length;
            count += switch (primitive.gltfMode()) {
                case 4 -> indexCount / 3L;
                case 5, 6 -> Math.max(0, indexCount - 2L);
                default -> 0L;
            };
        }
        return count;
    }

    public record CaptureResult(
            Optional<CapturedNode> node,
            CaptureState state,
            List<Diagnostic> diagnostics,
            BatchCounters counters) {
        public CaptureResult {
            node = Objects.requireNonNull(node, "node");
            Objects.requireNonNull(state, "state");
            diagnostics = List.copyOf(diagnostics);
            Objects.requireNonNull(counters, "counters");
        }
    }
}
