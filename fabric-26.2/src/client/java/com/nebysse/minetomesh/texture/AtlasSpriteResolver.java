package com.nebysse.minetomesh.texture;

import com.nebysse.minetomesh.scene.Vec2f;
import java.util.ArrayList;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

public final class AtlasSpriteResolver {
    private final AtlasLookup atlases;
    private final Map<TextureAtlas, AtlasData> cache = new IdentityHashMap<>();

    public AtlasSpriteResolver(AtlasLookup atlases) {
        this.atlases = Objects.requireNonNull(atlases, "atlases");
    }

    public Resolution resolve(TextureAtlasSprite declared, List<Vec2f> atlasUvs) {
        Objects.requireNonNull(declared, "declared");
        Objects.requireNonNull(atlasUvs, "atlasUvs");
        TextureAtlas atlas = Objects.requireNonNull(
                atlases.get(declared.atlasLocation()), "atlas");
        AtlasData data = cache.computeIfAbsent(atlas, AtlasSpriteResolver::buildData);
        Identifier declaredId = declared.contents().name();
        AtlasSpriteIndex.Resolution indexed = data.index().resolve(declaredId, atlasUvs);
        AtlasSpriteIndex.Region indexedRegion = indexed.region().orElse(null);
        if (indexedRegion == null) {
            AtlasSpriteIndex.Region fallback = region(
                    declaredId,
                    declared.contents().width(),
                    declared.contents().height(),
                    declared.getU0(), declared.getV0(),
                    declared.getU1(), declared.getV1());
            return new Resolution(
                    declared, fallback, declaredId, declaredId,
                    AtlasSpriteIndex.Kind.FALLBACK);
        }
        TextureAtlasSprite resolved = data.sprites().get(indexedRegion.id());
        if (resolved == null) {
            AtlasSpriteIndex.Region fallback = region(
                    declaredId,
                    declared.contents().width(),
                    declared.contents().height(),
                    declared.getU0(), declared.getV0(),
                    declared.getU1(), declared.getV1());
            return new Resolution(
                    declared, fallback, declaredId, declaredId,
                    AtlasSpriteIndex.Kind.FALLBACK);
        }
        return new Resolution(
                resolved,
                indexedRegion,
                declaredId,
                indexedRegion.id(),
                indexed.kind());
    }

    static AtlasSpriteIndex.Region region(
            Identifier id,
            int pixelWidth,
            int pixelHeight,
            float u0,
            float v0,
            float u1,
            float v1) {
        float width = u1 - u0;
        float height = v1 - v0;
        if (!(width > 0.0F) || !(height > 0.0F)) {
            throw new IllegalArgumentException("Sprite atlas bounds must have positive dimensions");
        }
        int atlasWidth = Math.round(pixelWidth / width);
        int atlasHeight = Math.round(pixelHeight / height);
        return new AtlasSpriteIndex.Region(
                id, u0, v0, u1, v1, atlasWidth, atlasHeight);
    }

    public static List<Vec2f> normalize(
            List<Vec2f> atlasUvs,
            AtlasSpriteIndex.Region region) {
        Objects.requireNonNull(atlasUvs, "atlasUvs");
        Objects.requireNonNull(region, "region");
        List<Vec2f> result = new ArrayList<>(atlasUvs.size());
        for (Vec2f uv : atlasUvs) {
            result.add(SpriteTextureExtractor.normalizeUv(
                    uv.x(), uv.y(),
                    region.u0(), region.v0(), region.u1(), region.v1()));
        }
        return List.copyOf(result);
    }

    private static AtlasData buildData(TextureAtlas atlas) {
        Map<Identifier, TextureAtlasSprite> sprites = new LinkedHashMap<>();
        List<AtlasSpriteIndex.Region> regions = new ArrayList<>();
        textures(atlas).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    TextureAtlasSprite sprite = entry.getValue();
                    sprites.put(entry.getKey(), sprite);
                    regions.add(region(
                            entry.getKey(),
                            sprite.contents().width(),
                            sprite.contents().height(),
                            sprite.getU0(), sprite.getV0(),
                            sprite.getU1(), sprite.getV1()));
                });
        return new AtlasData(
                new AtlasSpriteIndex(regions),
                Map.copyOf(sprites));
    }

    private static Map<Identifier, TextureAtlasSprite> textures(TextureAtlas atlas) {
        try {
            Field field = TextureAtlas.class.getDeclaredField("texturesByName");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Identifier, TextureAtlasSprite> textures =
                    (Map<Identifier, TextureAtlasSprite>) field.get(atlas);
            return textures;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect texture atlas", exception);
        }
    }

    @FunctionalInterface
    public interface AtlasLookup {
        TextureAtlas get(Identifier atlasLocation);
    }

    public record Resolution(
            TextureAtlasSprite sprite,
            AtlasSpriteIndex.Region region,
            Identifier declaredId,
            Identifier resolvedId,
            AtlasSpriteIndex.Kind kind) {
        public Resolution {
            Objects.requireNonNull(sprite, "sprite");
            Objects.requireNonNull(region, "region");
            Objects.requireNonNull(declaredId, "declaredId");
            Objects.requireNonNull(resolvedId, "resolvedId");
            Objects.requireNonNull(kind, "kind");
        }
    }

    private record AtlasData(
            AtlasSpriteIndex index,
            Map<Identifier, TextureAtlasSprite> sprites) {
    }
}
