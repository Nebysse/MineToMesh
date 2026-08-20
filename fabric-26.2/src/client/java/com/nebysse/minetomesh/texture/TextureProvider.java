package com.nebysse.minetomesh.texture;

import com.nebysse.minetomesh.scene.Diagnostic;
import com.nebysse.minetomesh.scene.TextureKey;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

public interface TextureProvider {
    String id();

    Optional<Result> acquire(Request request) throws Exception;

    record Request(
            Identifier textureId,
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
