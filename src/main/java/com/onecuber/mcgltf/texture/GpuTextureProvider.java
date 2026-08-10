package com.onecuber.mcgltf.texture;

import com.onecuber.mcgltf.scene.Diagnostic;
import com.onecuber.mcgltf.scene.TextureKey;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToIntFunction;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;

public final class GpuTextureProvider implements TextureProvider {
    private final GpuTextureAccess textureAccess;
    private final ToIntFunction<AbstractTexture> textureIdResolver;

    public GpuTextureProvider(GpuTextureAccess textureAccess) {
        this(textureAccess, AbstractTexture::getId);
    }

    GpuTextureProvider(
            GpuTextureAccess textureAccess,
            ToIntFunction<AbstractTexture> textureIdResolver) {
        this.textureAccess = Objects.requireNonNull(textureAccess, "textureAccess");
        this.textureIdResolver = Objects.requireNonNull(
                textureIdResolver, "textureIdResolver");
    }

    @Override
    public String id() {
        return "gpu";
    }

    @Override
    public Optional<Result> acquire(Request request) throws Exception {
        TextureManager manager = request.textureManager();
        if (manager == null) {
            return Optional.empty();
        }
        AbstractTexture texture = manager.getTexture(request.textureId());
        int textureId = textureIdResolver.applyAsInt(texture);
        if (textureId <= 0) {
            return Optional.empty();
        }
        GpuTextureAccess.Pixels pixels = textureAccess.readRgba8(textureId);
        byte[] rgba = pixels.rgba();
        String hash = ResourceTextureExtractor.sha256(rgba).substring(0, 16);
        TextureKey key = new TextureKey(
                TextureKey.Kind.DYNAMIC,
                request.textureId() + "#" + hash,
                "textures/generated/" + hash + ".png");
        TextureImage image = new TextureImage(
                pixels.width(), pixels.height(), rgba,
                Optional.empty(), Optional.empty(), Optional.empty());
        Diagnostic diagnostic = new Diagnostic(
                Diagnostic.Severity.INFO,
                "GPU_TEXTURE_READBACK_USED",
                request.textureId().toString(),
                Optional.empty(),
                "",
                "",
                "Read loaded OpenGL texture " + textureId);
        return Optional.of(new Result(id(), key, image, List.of(diagnostic)));
    }
}
