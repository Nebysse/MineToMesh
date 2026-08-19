package com.nebysse.minetomesh.capture;

import com.nebysse.minetomesh.material.MaterialResolver;
import com.nebysse.minetomesh.scene.BatchCounters;
import com.nebysse.minetomesh.scene.CoordinateTransform;
import com.nebysse.minetomesh.scene.Diagnostic;
import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.PrimitiveAccumulator;
import com.nebysse.minetomesh.scene.PrimitiveMode;
import com.nebysse.minetomesh.scene.Vec2f;
import com.nebysse.minetomesh.scene.Vec3f;
import com.nebysse.minetomesh.scene.Vertex;
import com.nebysse.minetomesh.texture.SpriteTextureExtractor;
import com.nebysse.minetomesh.texture.TextureRegistry;
import com.nebysse.minetomesh.world.BlockPoint;
import com.nebysse.minetomesh.world.Selection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.textures.FluidSpriteCache;

public final class FluidGeometryCapture {
    private static final float UV_EPSILON = 1.0E-5F;
    private static final CoordinateTransform LOCAL_TRANSFORM =
            new CoordinateTransform(new Vec3f(0.0F, 0.0F, 0.0F));

    private final SpriteTextureExtractor spriteExtractor;
    private final TextureRegistry textureRegistry;
    private final Map<TextureAtlasSprite, SpriteTextureExtractor.Extraction> spriteCache =
            new IdentityHashMap<>();

    public FluidGeometryCapture(
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
        FluidState fluid = level.getFluidState(position);
        if (fluid.isEmpty()) {
            return new CaptureResult(BatchCounters.ZERO, List.of());
        }
        String objectId = BuiltInRegistries.FLUID.getKey(fluid.getType()).toString();
        List<Diagnostic> diagnostics = new ArrayList<>();
        try {
            SelectionBlockView view = new SelectionBlockView(level, selection);
            CapturingVertexConsumer consumer = new CapturingVertexConsumer();
            BlockState state = level.getBlockState(position);
            Minecraft.getInstance().getBlockRenderer()
                    .renderLiquid(position, view, consumer, state, fluid);
            List<Vertex> captured = consumer.finish();
            TextureAtlasSprite[] sprites = FluidSpriteCache.getFluidSprites(view, position, fluid);
            List<SpriteEntry> entries = spriteEntries(sprites);
            RenderType renderType = ItemBlockRenderTypes.getRenderLayer(fluid);
            RenderTypeInspector.Inspection inspection = RenderTypeInspector.inspect(renderType);
            diagnostics.addAll(inspection.diagnostics());

            int complete = captured.size() - captured.size() % 4;
            for (int start = 0; start < complete; start += 4) {
                List<Vertex> quad = captured.subList(start, start + 4);
                Optional<SpriteBounds> classified = classifySprite(
                        quad.stream().map(Vertex::uv).toList(),
                        entries.stream().map(SpriteEntry::bounds).toList());
                SpriteEntry sprite = classified
                        .flatMap(bounds -> entries.stream()
                                .filter(entry -> entry.bounds().equals(bounds)).findFirst())
                        .orElse(entries.getFirst());
                if (classified.isEmpty()) {
                    diagnostics.add(new Diagnostic(
                            Diagnostic.Severity.WARNING,
                            "FLUID_SPRITE_UNRESOLVED",
                            objectId,
                            Optional.of(new BlockPoint(selection.min().dimension(),
                                    position.getX(), position.getY(), position.getZ())),
                            "",
                            "",
                            "Fluid quad UVs did not fit a declared fluid sprite"));
                }
                SpriteTextureExtractor.Extraction texture = texture(sprite.sprite());
                MaterialKey material = MaterialResolver.resolve(inspection.descriptor(), texture.key());
                sectionAccumulator.append(material, PrimitiveMode.QUADS,
                        transformQuad(quad, sprite.sprite(), position, selection));
            }
            if (complete != captured.size()) {
                diagnostics.add(new Diagnostic(
                        Diagnostic.Severity.WARNING,
                        "INCOMPLETE_PRIMITIVE",
                        objectId,
                        Optional.of(new BlockPoint(selection.min().dimension(),
                                position.getX(), position.getY(), position.getZ())),
                        "",
                        "",
                        "Discarded " + (captured.size() - complete) + " trailing fluid vertices"));
            }
            BatchCounters counters = complete == 0
                    ? BatchCounters.ZERO
                    : new BatchCounters(0, 0, 1, 0, 0, 0, 0, complete / 2L, 0);
            return new CaptureResult(counters, diagnostics);
        } catch (Exception exception) {
            diagnostics.add(new Diagnostic(
                    Diagnostic.Severity.FAILURE,
                    "FLUID_CAPTURE_FAILED",
                    objectId,
                    Optional.of(new BlockPoint(selection.min().dimension(),
                            position.getX(), position.getY(), position.getZ())),
                    "",
                    exception.getClass().getName(),
                    exception.getMessage() == null ? "Fluid capture failed" : exception.getMessage()));
            return new CaptureResult(BatchCounters.ZERO, diagnostics);
        }
    }

    public static Optional<SpriteBounds> classifySprite(
            List<Vec2f> uvs,
            List<SpriteBounds> sprites) {
        if (uvs.isEmpty()) {
            return Optional.empty();
        }
        return sprites.stream()
                .filter(sprite -> uvs.stream().allMatch(sprite::contains))
                .min(Comparator.comparingDouble(SpriteBounds::area));
    }

    private List<SpriteEntry> spriteEntries(TextureAtlasSprite[] sprites) throws IOException {
        List<SpriteEntry> entries = new ArrayList<>();
        for (int index = 0; index < sprites.length; index++) {
            TextureAtlasSprite sprite = sprites[index];
            if (sprite != null) {
                String name = switch (index) {
                    case 0 -> "still";
                    case 1 -> "flow";
                    default -> "overlay";
                };
                entries.add(new SpriteEntry(sprite, new SpriteBounds(
                        name, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1())));
            }
        }
        if (entries.isEmpty()) {
            throw new IOException("Fluid exposes no sprites");
        }
        return entries;
    }

    private SpriteTextureExtractor.Extraction texture(TextureAtlasSprite sprite) throws IOException {
        SpriteTextureExtractor.Extraction extraction = spriteCache.get(sprite);
        if (extraction == null) {
            extraction = spriteExtractor.extract(sprite);
            spriteCache.put(sprite, extraction);
            textureRegistry.register(extraction.key(), extraction.image());
        }
        return extraction;
    }

    private static List<Vertex> transformQuad(
            List<Vertex> quad,
            TextureAtlasSprite sprite,
            BlockPos position,
            Selection selection) {
        int sectionBaseX = Math.floorDiv(position.getX(), 16) * 16;
        int sectionBaseY = Math.floorDiv(position.getY(), 16) * 16;
        int sectionBaseZ = Math.floorDiv(position.getZ(), 16) * 16;
        return quad.stream().map(vertex -> {
            Vec3f localMinecraft = new Vec3f(
                    sectionBaseX + vertex.position().x() - selection.min().x(),
                    sectionBaseY + vertex.position().y() - selection.min().y(),
                    sectionBaseZ + vertex.position().z() - selection.min().z());
            return new Vertex(
                    LOCAL_TRANSFORM.position(localMinecraft),
                    LOCAL_TRANSFORM.normal(vertex.normal()),
                    SpriteTextureExtractor.normalizeUv(
                            vertex.uv().x(), vertex.uv().y(),
                            sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1()),
                    vertex.color());
        }).toList();
    }

    public record SpriteBounds(
            String name,
            float u0,
            float v0,
            float u1,
            float v1) {
        public SpriteBounds {
            Objects.requireNonNull(name, "name");
            if (!(u1 > u0) || !(v1 > v0)) {
                throw new IllegalArgumentException("Sprite bounds must have positive area");
            }
        }

        boolean contains(Vec2f uv) {
            return uv.x() >= u0 - UV_EPSILON && uv.x() <= u1 + UV_EPSILON
                    && uv.y() >= v0 - UV_EPSILON && uv.y() <= v1 + UV_EPSILON;
        }

        double area() {
            return (double) (u1 - u0) * (v1 - v0);
        }
    }

    private record SpriteEntry(TextureAtlasSprite sprite, SpriteBounds bounds) {
    }

    public record CaptureResult(BatchCounters counters, List<Diagnostic> diagnostics) {
        public CaptureResult {
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
