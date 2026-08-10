package com.onecuber.mcgltf.texture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.onecuber.mcgltf.scene.TextureKey;
import com.onecuber.mcgltf.scene.Vec2f;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class SpriteTextureExtractor {
    private final ResourceManager resourceManager;

    public SpriteTextureExtractor(ResourceManager resourceManager) {
        this.resourceManager = Objects.requireNonNull(resourceManager, "resourceManager");
    }

    public Extraction extract(TextureAtlasSprite sprite) throws IOException {
        Objects.requireNonNull(sprite, "sprite");
        SpriteContents contents = sprite.contents();
        ResourceLocation sourceId = contents.name();
        ResourceLocation pngId = sourceId.withPath("textures/" + sourceId.getPath() + ".png");
        ResourceLocation mcmetaId = pngId.withSuffix(".mcmeta");
        byte[] sourcePng = readRequired(pngId);
        Optional<byte[]> mcmeta = readOptional(mcmetaId);

        NativeImage source = contents.byMipLevel[0];
        Optional<TextureImage.AnimationInfo> animation = parseAnimation(
                mcmeta, contents.width(), contents.height(), source.getWidth(), source.getHeight());
        int firstFrame = animation.map(info -> info.frameOrder().getFirst()).orElse(0);
        int columns = source.getWidth() / contents.width();
        if (columns <= 0) {
            throw new IOException("Sprite frame width exceeds source image width: " + sourceId);
        }
        int sourceX = firstFrame % columns * contents.width();
        int sourceY = firstFrame / columns * contents.height();
        if (sourceX + contents.width() > source.getWidth()
                || sourceY + contents.height() > source.getHeight()) {
            throw new IOException("Sprite animation frame is outside source image: " + sourceId);
        }

        byte[] rgba = new byte[Math.multiplyExact(Math.multiplyExact(contents.width(), contents.height()), 4)];
        int cursor = 0;
        for (int y = 0; y < contents.height(); y++) {
            for (int x = 0; x < contents.width(); x++) {
                int abgr = source.getPixelRGBA(sourceX + x, sourceY + y);
                rgba[cursor++] = (byte) (abgr & 0xFF);
                rgba[cursor++] = (byte) (abgr >>> 8 & 0xFF);
                rgba[cursor++] = (byte) (abgr >>> 16 & 0xFF);
                rgba[cursor++] = (byte) (abgr >>> 24 & 0xFF);
            }
        }

        TextureImage image = new TextureImage(
                contents.width(), contents.height(), rgba,
                animation.isPresent() ? Optional.of(sourcePng) : Optional.empty(),
                animation.isPresent() ? mcmeta : Optional.empty(),
                animation);
        TextureKey key = new TextureKey(
                TextureKey.Kind.ATLAS_SPRITE,
                sourceId.toString(),
                "textures/" + sourceId.getNamespace() + "/" + sourceId.getPath() + ".png");
        return new Extraction(key, image);
    }

    public static Vec2f normalizeUv(
            float u, float v, float u0, float v0, float u1, float v1) {
        if (!Float.isFinite(u) || !Float.isFinite(v)
                || !Float.isFinite(u0) || !Float.isFinite(v0)
                || !Float.isFinite(u1) || !Float.isFinite(v1)) {
            throw new IllegalArgumentException("UV values must be finite");
        }
        float width = u1 - u0;
        float height = v1 - v0;
        if (width <= 0.0F || height <= 0.0F) {
            throw new IllegalArgumentException("Sprite UV bounds must have positive width and height");
        }
        return new Vec2f((u - u0) / width, (v - v0) / height);
    }

    private Optional<TextureImage.AnimationInfo> parseAnimation(
            Optional<byte[]> mcmeta,
            int logicalWidth,
            int logicalHeight,
            int imageWidth,
            int imageHeight) {
        int defaultTime = 1;
        boolean interpolate = false;
        List<Integer> order = new ArrayList<>();
        List<Integer> times = new ArrayList<>();
        int frameWidth = logicalWidth;
        int frameHeight = logicalHeight;
        if (mcmeta.isPresent()) {
            JsonObject root = JsonParser.parseString(
                    new String(mcmeta.orElseThrow(), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject animation = root.getAsJsonObject("animation");
            if (animation != null) {
                frameWidth = animation.has("width") ? animation.get("width").getAsInt() : logicalWidth;
                frameHeight = animation.has("height") ? animation.get("height").getAsInt() : logicalHeight;
                defaultTime = animation.has("frametime") ? animation.get("frametime").getAsInt() : 1;
                interpolate = animation.has("interpolate") && animation.get("interpolate").getAsBoolean();
                JsonArray frames = animation.getAsJsonArray("frames");
                if (frames != null) {
                    for (JsonElement frame : frames) {
                        if (frame.isJsonPrimitive()) {
                            order.add(frame.getAsInt());
                            times.add(defaultTime);
                        } else {
                            JsonObject value = frame.getAsJsonObject();
                            order.add(value.get("index").getAsInt());
                            times.add(value.has("time") ? value.get("time").getAsInt() : defaultTime);
                        }
                    }
                }
            }
        }
        int columns = imageWidth / frameWidth;
        int rows = imageHeight / frameHeight;
        int frameCount = Math.multiplyExact(columns, rows);
        if (order.isEmpty() && frameCount > 1) {
            for (int index = 0; index < frameCount; index++) {
                order.add(index);
                times.add(defaultTime);
            }
        }
        if (order.size() <= 1) {
            return Optional.empty();
        }
        return Optional.of(new TextureImage.AnimationInfo(
                frameWidth, frameHeight, order, times, interpolate));
    }

    private byte[] readRequired(ResourceLocation id) throws IOException {
        Resource resource = resourceManager.getResource(id)
                .orElseThrow(() -> new IOException("Missing texture resource: " + id));
        try (InputStream input = resource.open()) {
            return input.readAllBytes();
        }
    }

    private Optional<byte[]> readOptional(ResourceLocation id) throws IOException {
        Optional<Resource> resource = resourceManager.getResource(id);
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream input = resource.orElseThrow().open()) {
            return Optional.of(input.readAllBytes());
        }
    }

    public record Extraction(TextureKey key, TextureImage image) {
    }
}
