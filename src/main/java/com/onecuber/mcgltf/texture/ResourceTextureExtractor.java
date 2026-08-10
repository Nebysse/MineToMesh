package com.onecuber.mcgltf.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.onecuber.mcgltf.scene.Diagnostic;
import com.onecuber.mcgltf.scene.TextureKey;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class ResourceTextureExtractor {
    public static TextureProvider resourceProvider() {
        ResourceTextureExtractor extractor = new ResourceTextureExtractor();
        return new TextureProvider() {
            @Override
            public String id() {
                return "resource";
            }

            @Override
            public Optional<Result> acquire(Request request) throws Exception {
                ResourceManager manager = request.resourceManager();
                if (manager == null
                        || manager.getResource(pngResource(request.textureId())).isEmpty()) {
                    return Optional.empty();
                }
                Extraction extraction = extractor.extractResource(
                        request.textureId(), manager);
                return Optional.of(new Result(
                        id(), extraction.key(), extraction.image(), extraction.diagnostics()));
            }
        };
    }

    public static TextureProvider dynamicProvider() {
        ResourceTextureExtractor extractor = new ResourceTextureExtractor();
        return new TextureProvider() {
            @Override
            public String id() {
                return "dynamic";
            }

            @Override
            public Optional<Result> acquire(Request request) {
                TextureManager manager = request.textureManager();
                if (manager == null) {
                    return Optional.empty();
                }
                AbstractTexture texture = manager.getTexture(request.textureId());
                if (!(texture instanceof DynamicTexture dynamicTexture)
                        || dynamicTexture.getPixels() == null) {
                    return Optional.empty();
                }
                Extraction extraction = extractor.extractDynamic(
                        request.textureId(), dynamicTexture);
                return Optional.of(new Result(
                        id(), extraction.key(), extraction.image(), extraction.diagnostics()));
            }
        };
    }

    public Extraction extract(
            ResourceLocation textureId,
            ResourceManager resourceManager,
            TextureManager textureManager) throws IOException {
        Objects.requireNonNull(textureId, "textureId");
        Objects.requireNonNull(resourceManager, "resourceManager");
        Objects.requireNonNull(textureManager, "textureManager");
        AbstractTexture texture = textureManager.getTexture(textureId);
        if (texture instanceof DynamicTexture dynamicTexture) {
            return extractDynamic(textureId, dynamicTexture);
        }
        return extractResource(textureId, resourceManager);
    }

    public Extraction extractResource(ResourceLocation textureId, ResourceManager resourceManager) throws IOException {
        ResourceLocation pngId = pngResource(textureId);
        Resource resource = resourceManager.getResource(pngId)
                .orElseThrow(() -> new IOException("Missing texture resource: " + pngId));
        byte[] png;
        try (InputStream input = resource.open()) {
            png = input.readAllBytes();
        }
        try (NativeImage image = NativeImage.read(png)) {
            TextureImage textureImage = new TextureImage(
                    image.getWidth(), image.getHeight(), copyRgba(image),
                    Optional.of(png), Optional.empty(), Optional.empty());
            return new Extraction(keyForResource(textureId), textureImage, List.of());
        }
    }

    public Extraction extractDynamic(ResourceLocation textureId, DynamicTexture texture) {
        NativeImage pixels = texture.getPixels();
        if (pixels == null) {
            return failed(textureId, "Dynamic texture has no CPU pixel image");
        }
        byte[] rgba = copyRgba(pixels);
        String hash = sha256(rgba).substring(0, 16);
        TextureKey key = new TextureKey(
                TextureKey.Kind.DYNAMIC,
                textureId + "#" + hash,
                "textures/generated/" + hash + ".png");
        return new Extraction(key,
                new TextureImage(pixels.getWidth(), pixels.getHeight(), rgba,
                        Optional.empty(), Optional.empty(), Optional.empty()),
                List.of());
    }

    public static Extraction failed(ResourceLocation textureId, String message) {
        TextureKey key = new TextureKey(
                TextureKey.Kind.DYNAMIC,
                "mcgltf:missing_texture",
                "textures/generated/missing_texture.png");
        Diagnostic diagnostic = new Diagnostic(
                Diagnostic.Severity.WARNING,
                "TEXTURE_READ_FAILED",
                textureId.toString(),
                Optional.empty(),
                "",
                "",
                message);
        return new Extraction(key, checkerboard(), List.of(diagnostic));
    }

    private static TextureImage checkerboard() {
        int size = 16;
        byte[] rgba = new byte[size * size * 4];
        int cursor = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean purple = ((x / 4) + (y / 4)) % 2 == 0;
                rgba[cursor++] = (byte) (purple ? 255 : 0);
                rgba[cursor++] = 0;
                rgba[cursor++] = (byte) (purple ? 255 : 0);
                rgba[cursor++] = (byte) 255;
            }
        }
        return new TextureImage(size, size, rgba,
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    static byte[] copyRgba(NativeImage image) {
        byte[] rgba = new byte[Math.multiplyExact(Math.multiplyExact(
                image.getWidth(), image.getHeight()), 4)];
        int cursor = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int abgr = image.getPixelRGBA(x, y);
                rgba[cursor++] = (byte) (abgr & 0xFF);
                rgba[cursor++] = (byte) (abgr >>> 8 & 0xFF);
                rgba[cursor++] = (byte) (abgr >>> 16 & 0xFF);
                rgba[cursor++] = (byte) (abgr >>> 24 & 0xFF);
            }
        }
        return rgba;
    }

    private static ResourceLocation pngResource(ResourceLocation textureId) {
        String path = textureId.getPath();
        if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }
        if (!path.endsWith(".png")) {
            path += ".png";
        }
        return textureId.withPath(path);
    }

    private static TextureKey keyForResource(ResourceLocation textureId) {
        String path = textureId.getPath();
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - 4);
        }
        return new TextureKey(TextureKey.Kind.RESOURCE, textureId.toString(),
                "textures/" + textureId.getNamespace() + "/" + path + ".png");
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record Extraction(TextureKey key, TextureImage image, List<Diagnostic> diagnostics) {
        public Extraction {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(image, "image");
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
