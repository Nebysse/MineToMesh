package com.onecuber.mcgltf.texture;

import com.onecuber.mcgltf.scene.Diagnostic;
import com.onecuber.mcgltf.scene.TextureKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TextureAcquisitionChain {
    private final List<TextureProvider> providers;
    private final Fallback fallback;

    public TextureAcquisitionChain(
            List<TextureProvider> providers,
            Fallback fallback) {
        this.providers = List.copyOf(providers);
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    public TextureProvider.Result acquire(TextureProvider.Request request) {
        Objects.requireNonNull(request, "request");
        List<Attempt> attempts = new ArrayList<>();
        for (TextureProvider provider : providers) {
            try {
                Optional<TextureProvider.Result> result = provider.acquire(request);
                if (result.isPresent()) {
                    TextureProvider.Result value = result.orElseThrow();
                    return new TextureProvider.Result(
                            provider.id(), value.key(), value.image(), value.diagnostics());
                }
                attempts.add(new Attempt(provider.id(), "unavailable"));
            } catch (Exception exception) {
                attempts.add(new Attempt(
                        provider.id(),
                        exception.getMessage() == null
                                ? exception.getClass().getSimpleName()
                                : exception.getMessage()));
            }
        }
        return fallback.create(request, attempts);
    }

    public static TextureProvider.Result missing(
            TextureProvider.Request request,
            List<Attempt> attempts) {
        String message = attempts.stream()
                .map(attempt -> attempt.providerId() + ": " + attempt.reason())
                .reduce((left, right) -> left + "; " + right)
                .orElse("No texture providers configured");
        Diagnostic diagnostic = new Diagnostic(
                Diagnostic.Severity.WARNING,
                "TEXTURE_READ_FAILED",
                request.textureId().toString(),
                Optional.empty(),
                "",
                "",
                message);
        return new TextureProvider.Result(
                "missing",
                new TextureKey(
                        TextureKey.Kind.DYNAMIC,
                        "mcgltf:missing_texture",
                        "textures/generated/missing_texture.png"),
                checkerboard(),
                List.of(diagnostic));
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
        return new TextureImage(
                size, size, rgba,
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    @FunctionalInterface
    public interface Fallback {
        TextureProvider.Result create(
                TextureProvider.Request request,
                List<Attempt> attempts);
    }

    public record Attempt(String providerId, String reason) {
        public Attempt {
            Objects.requireNonNull(providerId, "providerId");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
