package com.nebysse.minetomesh.capture;

import com.nebysse.minetomesh.material.MaterialResolver;
import com.nebysse.minetomesh.scene.BatchCounters;
import com.nebysse.minetomesh.scene.ColorRgba;
import com.nebysse.minetomesh.scene.Diagnostic;
import com.nebysse.minetomesh.scene.GeometryAdjustmentStats;
import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.PrimitiveAccumulator;
import com.nebysse.minetomesh.scene.Vec2f;
import com.nebysse.minetomesh.scene.Vec3f;
import com.nebysse.minetomesh.scene.Vertex;
import com.nebysse.minetomesh.texture.AtlasSpriteIndex;
import com.nebysse.minetomesh.texture.AtlasSpriteResolver;
import com.nebysse.minetomesh.texture.SpriteTextureExtractor;
import com.nebysse.minetomesh.texture.TextureRegistry;
import com.nebysse.minetomesh.world.BlockPoint;
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
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class BlockModelExtractor {
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
        this.atlasSpriteResolver = Objects.requireNonNull(
                atlasSpriteResolver, "atlasSpriteResolver");
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
        BlockState state = level.getBlockState(position);
        if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
            return new CaptureResult(
                    CaptureState.EMPTY, BatchCounters.ZERO, List.of(),
                    GeometryAdjustmentStats.ZERO);
        }

        String objectId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        List<PendingStream> pending = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        try {
            BlockStateModel model = Minecraft.getInstance().getModelManager()
                    .getBlockStateModelSet().get(state);
            long seed = state.getSeed(position);
            List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(RandomSource.create(seed), parts);
            for (BlockStateModelPart part : parts) {
                for (Direction direction : BlockQuadPolicy.directions()) {
                    if (direction != null
                            && !shouldRenderFace(
                                    level, selection, state, position, direction)) {
                        continue;
                    }
                    for (BakedQuad quad : part.getQuads(direction)) {
                        RenderType renderType = quad.materialInfo().itemRenderType();
                        RenderTypeInspector.Inspection inspection =
                                RenderTypeInspector.inspect(renderType);
                        diagnostics.addAll(inspection.diagnostics());
                        if (!inspection.descriptor().discard()) {
                            pending.add(captureQuad(
                                    level, selection, state, position, objectId,
                                    inspection.descriptor(), quad, diagnostics));
                        }
                    }
                }
            }
            CoplanarQuadLayering.Result layered = CoplanarQuadLayering.apply(
                    pending.stream().map(PendingStream::vertices).toList());
            for (int index = 0; index < pending.size(); index++) {
                PendingStream stream = pending.get(index);
                List<Vertex> vertices = layered.quads().get(index);
                PrimitiveAccumulator target = stream.route()
                        == BlockPrimitiveRouter.Route.GLOBAL_GRASS_SIDE_OVERLAY
                        ? overlayAccumulator : sectionAccumulator;
                target.append(stream.material(), stream.mode(), vertices);
            }
            CoplanarQuadLayering.Statistics stats = layered.statistics();
            GeometryAdjustmentStats adjustments = GeometryAdjustmentStats.forBlock(
                    objectId, stats.coplanarGroups(), stats.offsetFaces(),
                    stats.maxLayers());
            BatchCounters counters = pending.isEmpty()
                    ? BatchCounters.ZERO
                    : new BatchCounters(
                            0, 1, 0, 0, 0, 0, 0,
                            pending.size() * 2L, 0);
            return new CaptureResult(
                    pending.isEmpty() ? CaptureState.EMPTY : CaptureState.GEOMETRY,
                    counters, diagnostics, adjustments);
        } catch (Exception exception) {
            diagnostics.add(failure(
                    "BLOCK_MODEL_CAPTURE_FAILED", objectId,
                    position, selection, exception));
            return new CaptureResult(
                    CaptureState.FAILED, BatchCounters.ZERO, diagnostics,
                    GeometryAdjustmentStats.ZERO);
        }
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
        TextureAtlasSprite declaredSprite = quad.materialInfo().sprite();
        Vec3 offset = state.getOffset(position);
        int tint = tint(level, state, position, quad.materialInfo().tintIndex());
        ColorRgba color = new ColorRgba(
                tint >> 16 & 0xFF, tint >> 8 & 0xFF,
                tint & 0xFF, tint >>> 24);
        Direction direction = quad.direction();
        Vec3f normal = new Vec3f(
                direction.getStepX(), direction.getStepY(), direction.getStepZ());
        List<Vertex> raw = new ArrayList<>(4);
        for (int index = 0; index < 4; index++) {
            var source = quad.position(index);
            raw.add(new Vertex(
                    new Vec3f(
                            (float) (position.getX() - selection.min().x()
                                    + offset.x + source.x()),
                            (float) (position.getY() - selection.min().y()
                                    + offset.y + source.y()),
                            (float) (position.getZ() - selection.min().z()
                                    + offset.z + source.z())),
                    normal,
                    new Vec2f(
                            UVPair.unpackU(quad.packedUV(index)),
                            UVPair.unpackV(quad.packedUV(index))),
                    color));
        }
        List<Vec2f> atlasUvs = raw.stream().map(Vertex::uv).toList();
        AtlasSpriteResolver.Resolution resolution =
                atlasSpriteResolver.resolve(declaredSprite, atlasUvs);
        reportResolution(
                resolution, atlasUvs, selection, position,
                objectId, quad, diagnostics);
        SpriteTextureExtractor.Extraction texture = extraction(resolution.sprite());
        MaterialKey material = MaterialResolver.resolve(descriptor, texture.key());
        List<Vec2f> normalized = AtlasSpriteResolver.normalize(
                atlasUvs, resolution.region());
        List<Vertex> vertices = new ArrayList<>(4);
        for (int index = 0; index < raw.size(); index++) {
            Vertex vertex = raw.get(index);
            vertices.add(new Vertex(
                    vertex.position(), vertex.normal(),
                    normalized.get(index), vertex.color()));
        }
        return new PendingStream(
                material, descriptor.primitiveMode(), vertices,
                BlockPrimitiveRouter.route(texture.key()));
    }

    private static int tint(
            ClientLevel level, BlockState state, BlockPos position,
            int tintIndex) {
        if (tintIndex < 0) {
            return 0xFFFFFFFF;
        }
        BlockTintSource source = Minecraft.getInstance().getBlockColors()
                .getTintSource(state, tintIndex);
        return source == null
                ? 0xFFFFFFFF
                : source.colorInWorld(state, level, position);
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
        Optional<BlockPoint> point = Optional.of(new BlockPoint(
                selection.min().dimension(),
                position.getX(), position.getY(), position.getZ()));
        if (resolution.kind() == AtlasSpriteIndex.Kind.REDIRECTED) {
            String key = resolution.declaredId() + "->" + resolution.resolvedId();
            if (reportedRedirects.add(key)) {
                diagnostics.add(new Diagnostic(
                        Diagnostic.Severity.INFO,
                        "ATLAS_SPRITE_REDIRECTED", objectId, point,
                        quad.getClass().getName(), "",
                        "Resolved atlas sprite " + resolution.declaredId()
                                + " to " + resolution.resolvedId()));
            }
        } else if (resolution.kind() == AtlasSpriteIndex.Kind.FALLBACK
                && reportedFailures.add(resolution.declaredId().toString())) {
            diagnostics.add(new Diagnostic(
                    Diagnostic.Severity.WARNING,
                    "ATLAS_SPRITE_RESOLUTION_FAILED", objectId, point,
                    quad.getClass().getName(), "",
                    "No atlas sprite covered " + uvBounds(atlasUvs)
                            + "; kept declared sprite "
                            + resolution.declaredId()));
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
        return String.format(Locale.ROOT,
                "UV [%.6f, %.6f]-[%.6f, %.6f]",
                minU, minV, maxU, maxV);
    }

    private static boolean shouldRenderFace(
            ClientLevel level,
            Selection selection,
            BlockState state,
            BlockPos position,
            Direction direction) {
        BlockPos neighbor = position.relative(direction);
        boolean inside = selection.contains(
                neighbor.getX(), neighbor.getY(), neighbor.getZ());
        boolean loaded = level.hasChunk(
                Math.floorDiv(neighbor.getX(), 16),
                Math.floorDiv(neighbor.getZ(), 16));
        return BlockQuadPolicy.shouldRenderFace(
                inside, loaded,
                () -> Block.shouldRenderFace(
                        state, level.getBlockState(neighbor), direction));
    }

    private static Diagnostic failure(
            String code,
            String objectId,
            BlockPos position,
            Selection selection,
            Exception exception) {
        return new Diagnostic(
                Diagnostic.Severity.FAILURE, code, objectId,
                Optional.of(new BlockPoint(
                        selection.min().dimension(), position.getX(),
                        position.getY(), position.getZ())),
                "", exception.getClass().getName(),
                exception.getMessage() == null
                        ? "Block model capture failed"
                        : exception.getMessage());
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
            diagnostics = List.copyOf(diagnostics);
        }

        public boolean hasGeometry() {
            return state == CaptureState.GEOMETRY;
        }
    }
}
