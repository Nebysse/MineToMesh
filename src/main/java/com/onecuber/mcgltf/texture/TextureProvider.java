package com.onecuber.mcgltf.texture;

import com.onecuber.mcgltf.scene.Diagnostic;
import com.onecuber.mcgltf.scene.TextureKey;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public interface TextureProvider {
    String id();

    Optional<Result> acquire(Request request) throws Exception;

    record Request(
            ResourceLocation textureId,
            ResourceManager resourceManager,
            TextureManager textureManager) {
        public Request {
            Objects.requireNonNull(textureId, "textureId");
        }
    }

    record Result(
            String providerId,
            TextureKey key,
            TextureImage image,
            List<Diagnostic> diagnostics) {
        public Result {
            Objects.requireNonNull(providerId, "providerId");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(image, "image");
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
