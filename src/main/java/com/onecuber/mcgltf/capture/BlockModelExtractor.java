package com.onecuber.mcgltf.capture;

import com.mojang.blaze3d.vertex.PoseStack;
import com.onecuber.mcgltf.material.MaterialResolver;
import com.onecuber.mcgltf.scene.BatchCounters;
import com.onecuber.mcgltf.scene.CoordinateTransform;
import com.onecuber.mcgltf.scene.Diagnostic;
import com.onecuber.mcgltf.scene.MaterialKey;
import com.onecuber.mcgltf.scene.PrimitiveAccumulator;
import com.onecuber.mcgltf.scene.Vertex;
import com.onecuber.mcgltf.scene.Vec3f;
import com.onecuber.mcgltf.texture.SpriteTextureExtractor;
import com.onecuber.mcgltf.texture.TextureRegistry;
import com.onecuber.mcgltf.world.Selection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.minecraft.client.multiplayer.ClientLevel;

public final class BlockModelExtractor {
    private static final CoordinateTransform LOCAL_TRANSFORM =
            new CoordinateTransform(new Vec3f(0.0F, 0.0F, 0.0F));

    private final SpriteTextureExtractor spriteExtractor;
    private final TextureRegistry textureRegistry;
    private final Map<TextureAtlasSprite, SpriteTextureExtractor.Extraction> spriteCache =
            new IdentityHashMap<>();

    public BlockModelExtractor(
            SpriteTextureExtractor spriteExtractor,
            TextureRegistry textureRegistry) {
        this.spriteExtractor = Objects.requireNonNull(spriteExtractor, "spriteExtractor");
        this.textureRegistry = Objects.requireNonNull(textureRegistry, "textureRegistry");
    }

    public CaptureResult capture(
            ClientLevel level,
            BlockPos position,
            Selection selection,
            PrimitiveAccumulator sectionAccumulator) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(sectionAccumulator, "sectionAccumulator");
        BlockState state = level.getBlockState(position);
        if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
            return new CaptureResult(CaptureState.EMPTY, BatchCounters.ZERO, List.of());
        }

        String objectId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        List<PendingStream> pending = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        Set<String> inspectedRenderTypes = new HashSet<>();
        try {
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            ModelData data = model.getModelData(level, position, state, level.getModelData(position));
            long seed = state.getSeed(position);
            RandomSource renderTypeRandom = RandomSource.create(seed);
            var renderTypes = model.getRenderTypes(state, renderTypeRandom, data);
            for (RenderType renderType : renderTypes) {
                RenderTypeInspector.Inspection inspection = RenderTypeInspector.inspect(renderType);
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
                    RandomSource quadRandom = RandomSource.create();
                    quadRandom.setSeed(seed);
                    List<BakedQuad> quads = model.getQuads(
                            state, direction, quadRandom, data, renderType);
                    for (BakedQuad quad : quads) {
                        pending.add(captureQuad(
                                level, selection, state, position, objectId,
                                descriptor, quad));
                    }
                }
            }
        } catch (Exception exception) {
            diagnostics.add(new Diagnostic(
                    Diagnostic.Severity.FAILURE,
                    "BLOCK_MODEL_CAPTURE_FAILED",
                    objectId,
                    Optional.of(new com.onecuber.mcgltf.world.BlockPoint(
                            selection.min().dimension(), position.getX(), position.getY(), position.getZ())),
                    "",
                    exception.getClass().getName(),
                    exception.getMessage() == null ? "Block model capture failed" : exception.getMessage()));
            return new CaptureResult(CaptureState.FAILED, BatchCounters.ZERO, diagnostics);
        }

        for (PendingStream stream : pending) {
            sectionAccumulator.append(stream.material(), stream.mode(), stream.vertices());
        }
        BatchCounters counters = pending.isEmpty()
                ? BatchCounters.ZERO
                : new BatchCounters(0, 1, 0, 0, 0, 0, 0,
                        pending.size() * 2L, 0);
        CaptureState captureState = pending.isEmpty()
                ? CaptureState.EMPTY : CaptureState.GEOMETRY;
        return new CaptureResult(captureState, counters, diagnostics);
    }

    private PendingStream captureQuad(
            ClientLevel level,
            Selection selection,
            BlockState state,
            BlockPos position,
            String objectId,
            RenderTypeDescriptor descriptor,
            BakedQuad quad) throws IOException {
        TextureAtlasSprite sprite = quad.getSprite();
        SpriteTextureExtractor.Extraction texture = spriteCache.get(sprite);
        if (texture == null) {
            texture = spriteExtractor.extract(sprite);
            spriteCache.put(sprite, texture);
            textureRegistry.register(texture.key(), texture.image());
        }
        MaterialKey material = MaterialResolver.resolve(descriptor, texture.key());

        PoseStack poseStack = new PoseStack();
        Vec3 offset = state.getOffset(level, position);
        poseStack.translate(
                position.getX() - selection.min().x() + offset.x,
                position.getY() - selection.min().y() + offset.y,
                position.getZ() - selection.min().z() + offset.z);
        float red = 1.0F;
        float green = 1.0F;
        float blue = 1.0F;
        if (quad.isTinted()) {
            int tint = Minecraft.getInstance().getBlockColors()
                    .getColor(state, level, position, quad.getTintIndex());
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
        List<Vertex> vertices = consumer.finish().stream()
                .map(vertex -> new Vertex(
                        LOCAL_TRANSFORM.position(vertex.position()),
                        LOCAL_TRANSFORM.normal(vertex.normal()),
                        SpriteTextureExtractor.normalizeUv(
                                vertex.uv().x(), vertex.uv().y(),
                                sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1()),
                        vertex.color()))
                .toList();
        return new PendingStream(material, descriptor.primitiveMode(), vertices);
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
                () -> Block.shouldRenderFace(state, level, position, direction, neighbor));
    }

    private record PendingStream(
            MaterialKey material,
            com.onecuber.mcgltf.scene.PrimitiveMode mode,
            List<Vertex> vertices) {
    }

    public record CaptureResult(
            CaptureState state,
            BatchCounters counters,
            List<Diagnostic> diagnostics) {
        public CaptureResult {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(counters, "counters");
            diagnostics = List.copyOf(diagnostics);
        }

        public boolean hasGeometry() {
            return state == CaptureState.GEOMETRY;
        }
    }
}
