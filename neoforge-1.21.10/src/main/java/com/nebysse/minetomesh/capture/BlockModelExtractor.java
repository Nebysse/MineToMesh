package com.nebysse.minetomesh.capture;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nebysse.minetomesh.material.MaterialResolver;
import com.nebysse.minetomesh.scene.BatchCounters;
import com.nebysse.minetomesh.scene.CoordinateTransform;
import com.nebysse.minetomesh.scene.Diagnostic;
import com.nebysse.minetomesh.scene.GeometryAdjustmentStats;
import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.PrimitiveAccumulator;
import com.nebysse.minetomesh.scene.Vertex;
import com.nebysse.minetomesh.scene.Vec2f;
import com.nebysse.minetomesh.scene.Vec3f;
import com.nebysse.minetomesh.texture.AtlasSpriteIndex;
import com.nebysse.minetomesh.texture.AtlasSpriteResolver;
import com.nebysse.minetomesh.texture.SpriteTextureExtractor;
import com.nebysse.minetomesh.texture.TextureRegistry;
import com.nebysse.minetomesh.world.Selection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.multiplayer.ClientLevel;

public final class BlockModelExtractor {
    private static final CoordinateTransform LOCAL_TRANSFORM =
            new CoordinateTransform(new Vec3f(0.0F, 0.0F, 0.0F));

    private final SpriteTextureExtractor spriteExtractor;
    private final AtlasSpriteResolver atlasSpriteResolver;
    private final TextureRegistry textureRegistry;
    private final Map<TextureAtlasSprite, SpriteTextureExtractor.Extraction> spriteCache =
            new IdentityHashMap<>();
    private final Set<String> reportedRedirects = new HashSet<>();
    private final Set<String> reportedFailures = new HashSet<>();

    public BlockModelExtractor(
            SpriteTextureExtractor spriteExtractor,
            AtlasSpriteResolver atlasSpriteResolver,
            TextureRegistry textureRegistry) {
        this.spriteExtractor = Objects.requireNonNull(spriteExtractor, "spriteExtractor");
        this.atlasSpriteResolver = Objects.requireNonNull(atlasSpriteResolver, "atlasSpriteResolver");
        this.textureRegistry = Objects.requireNonNull(textureRegistry, "textureRegistry");
    }

    public CaptureResult capture(
            ClientLevel level,
            BlockPos position,
            Selection selection,
            PrimitiveAccumulator sectionAccumulator,
            PrimitiveAccumulator overlayAccumulator) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(sectionAccumulator, "sectionAccumulator");
        Objects.requireNonNull(overlayAccumulator, "overlayAccumulator");
        BlockState state = level.getBlockState(position);
        if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
            return new CaptureResult(CaptureState.EMPTY, BatchCounters.ZERO, List.of(),
                    GeometryAdjustmentStats.ZERO);
        }

        String objectId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        List<PendingStream> pending = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        Set<String> inspectedRenderTypes = new HashSet<>();
        CoplanarQuadLayering.Statistics layeringStats = CoplanarQuadLayering.Statistics.ZERO;
        GeometryAdjustmentStats adjustments = GeometryAdjustmentStats.ZERO;
        try {
            BlockStateModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            long seed = state.getSeed(position);
            List<BlockModelPart> parts = new ArrayList<>();
            model.collectParts(level, position, state, RandomSource.create(seed), parts);
            for (BlockModelPart part : parts) {
                RenderTypeInspector.Inspection inspection =
                        RenderTypeInspector.inspectLayer(part.getRenderType(state));
                RenderTypeDescriptor descriptor = inspection.descriptor();
                if (inspectedRenderTypes.add(descriptor.name())) {
                    diagnostics.addAll(inspection.diagnostics());
                }
                if (descriptor.discard()) {
                    continue;
                }
                for (Direction direction : BlockQuadPolicy.directions()) {
                    if (direction != null && !shouldRenderFace(level, selection, state, position, direction)) {
                        continue;
                    }
                    for (BakedQuad quad : part.getQuads(direction)) {
                        pending.add(captureQuad(
                                level, selection, state, position, objectId,
                                descriptor, quad, diagnostics));
                    }
                }
            }
            CoplanarQuadLayering.Result layered = CoplanarQuadLayering.apply(
                    pending.stream().map(PendingStream::vertices).toList());
            for (int index = 0; index < pending.size(); index++) {
                PendingStream source = pending.get(index);
                pending.set(index, new PendingStream(
                        source.material(), source.mode(),
                        layered.quads().get(index), source.route()));
            }
            layeringStats = layered.statistics();
            adjustments = GeometryAdjustmentStats.forBlock(
                    objectId,
                    layeringStats.coplanarGroups(),
                    layeringStats.offsetFaces(),
                    layeringStats.maxLayers());
        } catch (Exception exception) {
            diagnostics.add(new Diagnostic(
                    Diagnostic.Severity.FAILURE,
                    "BLOCK_MODEL_CAPTURE_FAILED",
                    objectId,
                    Optional.of(new com.nebysse.minetomesh.world.BlockPoint(
                            selection.min().dimension(), position.getX(), position.getY(), position.getZ())),
                    "",
                    exception.getClass().getName(),
                    exception.getMessage() == null ? "Block model capture failed" : exception.getMessage()));
            return new CaptureResult(CaptureState.FAILED, BatchCounters.ZERO, diagnostics,
                    GeometryAdjustmentStats.ZERO);
        }

        if (layeringStats.invalidNormals() > 0) {
            diagnostics.add(new Diagnostic(
                    Diagnostic.Severity.WARNING,
                    "COPLANAR_FACE_NORMAL_INVALID",
                    objectId,
                    Optional.of(new com.nebysse.minetomesh.world.BlockPoint(
                            selection.min().dimension(), position.getX(), position.getY(), position.getZ())),
                    "",
                    "",
                    "Could not offset " + layeringStats.invalidNormals()
                            + " coincident face layer(s) because their normals were degenerate"));
        }

        for (PendingStream stream : pending) {
            PrimitiveAccumulator target = stream.route()
                    == BlockPrimitiveRouter.Route.GLOBAL_GRASS_SIDE_OVERLAY
                    ? overlayAccumulator : sectionAccumulator;
            target.append(stream.material(), stream.mode(), stream.vertices());
        }
        BatchCounters counters = pending.isEmpty()
                ? BatchCounters.ZERO
                : new BatchCounters(0, 1, 0, 0, 0, 0, 0,
                        pending.size() * 2L, 0);
        CaptureState captureState = pending.isEmpty()
                ? CaptureState.EMPTY : CaptureState.GEOMETRY;
        return new CaptureResult(captureState, counters, diagnostics, adjustments);
    }

    private PendingStream captureQuad(
            ClientLevel level,
            Selection selection,
            BlockState state,
            BlockPos position,
            String objectId,
            RenderTypeDescriptor descriptor,
            BakedQuad quad,
            List<Diagnostic> diagnostics) throws IOException {
        PoseStack poseStack = new PoseStack();
        Vec3 offset = state.getOffset(position);
        poseStack.translate(
                position.getX() - selection.min().x() + offset.x,
                position.getY() - selection.min().y() + offset.y,
                position.getZ() - selection.min().z() + offset.z);
        float red = 1.0F;
        float green = 1.0F;
        float blue = 1.0F;
        if (quad.isTinted()) {
            int tint = Minecraft.getInstance().getBlockColors()
                    .getColor(state, level, position, quad.tintIndex());
            if (tint != -1) {
                red = (float) (tint >> 16 & 0xFF) / 255.0F;
                green = (float) (tint >> 8 & 0xFF) / 255.0F;
                blue = (float) (tint & 0xFF) / 255.0F;
            }
        }
        CapturingVertexConsumer consumer = new CapturingVertexConsumer();
        consumer.putBulkData(
                poseStack.last(),
                quad,
                new float[] {1.0F, 1.0F, 1.0F, 1.0F},
                red,
                green,
                blue,
                1.0F,
                new int[] {
                    LightTexture.FULL_BRIGHT,
                    LightTexture.FULL_BRIGHT,
                    LightTexture.FULL_BRIGHT,
                    LightTexture.FULL_BRIGHT},
                OverlayTexture.NO_OVERLAY,
                true);
        List<Vertex> rawVertices = consumer.finish();
        List<Vec2f> atlasUvs = rawVertices.stream().map(Vertex::uv).toList();
        AtlasSpriteResolver.Resolution resolution =
                atlasSpriteResolver.resolve(quad.sprite(), atlasUvs);
        reportResolution(resolution, atlasUvs, selection, position, objectId, quad, diagnostics);
        TextureAtlasSprite sprite = resolution.sprite();
        SpriteTextureExtractor.Extraction texture = extraction(sprite);
        MaterialKey material = MaterialResolver.resolve(descriptor, texture.key());
        List<Vec2f> normalizedUvs = AtlasSpriteResolver.normalize(
                atlasUvs, resolution.region());
        List<Vertex> vertices = new ArrayList<>(rawVertices.size());
        for (int index = 0; index < rawVertices.size(); index++) {
            Vertex vertex = rawVertices.get(index);
            vertices.add(new Vertex(
                    LOCAL_TRANSFORM.position(vertex.position()),
                    LOCAL_TRANSFORM.normal(vertex.normal()),
                    normalizedUvs.get(index),
                    vertex.color()));
        }
        return new PendingStream(
                material,
                descriptor.primitiveMode(),
                vertices,
                BlockPrimitiveRouter.route(texture.key()));
    }

    private SpriteTextureExtractor.Extraction extraction(
            TextureAtlasSprite sprite) throws IOException {
        SpriteTextureExtractor.Extraction texture = spriteCache.get(sprite);
        if (texture == null) {
            texture = spriteExtractor.extract(sprite);
            spriteCache.put(sprite, texture);
            textureRegistry.register(texture.key(), texture.image());
        }
        return texture;
    }

    private void reportResolution(
            AtlasSpriteResolver.Resolution resolution,
            List<Vec2f> atlasUvs,
            Selection selection,
            BlockPos position,
            String objectId,
            BakedQuad quad,
            List<Diagnostic> diagnostics) {
        String rendererClass = quad.getClass().getName();
        Optional<com.nebysse.minetomesh.world.BlockPoint> diagnosticPosition = Optional.of(
                new com.nebysse.minetomesh.world.BlockPoint(
                        selection.min().dimension(),
                        position.getX(), position.getY(), position.getZ()));
        if (resolution.kind() == AtlasSpriteIndex.Kind.REDIRECTED) {
            String key = resolution.declaredId() + "->" + resolution.resolvedId();
            if (reportedRedirects.add(key)) {
                diagnostics.add(new Diagnostic(
                        Diagnostic.Severity.INFO,
                        "ATLAS_SPRITE_REDIRECTED",
                        objectId,
                        diagnosticPosition,
                        rendererClass,
                        "",
                        "Resolved atlas sprite " + resolution.declaredId()
                                + " to " + resolution.resolvedId()));
            }
        } else if (resolution.kind() == AtlasSpriteIndex.Kind.FALLBACK
                && reportedFailures.add(resolution.declaredId().toString())) {
            diagnostics.add(new Diagnostic(
                    Diagnostic.Severity.WARNING,
                    "ATLAS_SPRITE_RESOLUTION_FAILED",
                    objectId,
                    diagnosticPosition,
                    rendererClass,
                    "",
                    "No atlas sprite covered " + uvBounds(atlasUvs)
                            + "; kept declared sprite " + resolution.declaredId()));
        }
    }

    private static String uvBounds(List<Vec2f> uvs) {
        float minU = Float.POSITIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY;
        float maxU = Float.NEGATIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        for (Vec2f uv : uvs) {
            minU = Math.min(minU, uv.x());
            minV = Math.min(minV, uv.y());
            maxU = Math.max(maxU, uv.x());
            maxV = Math.max(maxV, uv.y());
        }
        return String.format(Locale.ROOT, "UV [%.6f, %.6f]-[%.6f, %.6f]",
                minU, minV, maxU, maxV);
    }

    private static boolean shouldRenderFace(
            ClientLevel level,
            Selection selection,
            BlockState state,
            BlockPos position,
            Direction direction) {
        BlockPos neighbor = position.relative(direction);
        boolean inside = selection.contains(neighbor.getX(), neighbor.getY(), neighbor.getZ());
        boolean loaded = level.hasChunk(
                Math.floorDiv(neighbor.getX(), 16),
                Math.floorDiv(neighbor.getZ(), 16));
        return BlockQuadPolicy.shouldRenderFace(
                inside,
                loaded,
                () -> Block.shouldRenderFace(state, level.getBlockState(neighbor), direction));
    }

    private record PendingStream(
            MaterialKey material,
            com.nebysse.minetomesh.scene.PrimitiveMode mode,
            List<Vertex> vertices,
            BlockPrimitiveRouter.Route route) {
    }

    public record CaptureResult(
            CaptureState state,
            BatchCounters counters,
            List<Diagnostic> diagnostics,
            GeometryAdjustmentStats adjustments) {
        public CaptureResult {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(counters, "counters");
            diagnostics = List.copyOf(diagnostics);
            Objects.requireNonNull(adjustments, "adjustments");
        }

        public CaptureResult(
                CaptureState state,
                BatchCounters counters,
                List<Diagnostic> diagnostics) {
            this(state, counters, diagnostics, GeometryAdjustmentStats.ZERO);
        }

        public boolean hasGeometry() {
            return state == CaptureState.GEOMETRY;
        }
    }
}
