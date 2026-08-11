package com.nebysse.minetomesh.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nebysse.minetomesh.scene.TextureKey;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class TextureAcquisitionChainTest {
    @Test
    void stopsAtFirstSuccessfulProvider() {
        FakeProvider resource = FakeProvider.miss("resource");
        FakeProvider dynamic = FakeProvider.success(
                "dynamic", extraction("dynamic"));
        FakeProvider gpu = FakeProvider.success("gpu", extraction("gpu"));
        TextureAcquisitionChain chain = new TextureAcquisitionChain(
                List.of(resource, dynamic, gpu), TextureAcquisitionChain::missing);

        TextureProvider.Result result = chain.acquire(request());

        assertEquals("dynamic", result.providerId());
        assertEquals(1, resource.calls);
        assertEquals(1, dynamic.calls);
        assertEquals(0, gpu.calls);
    }

    @Test
    void recordsEveryFailureBeforeCheckerboardFallback() {
        TextureAcquisitionChain chain = new TextureAcquisitionChain(
                List.of(
                        FakeProvider.failure("resource", "missing"),
                        FakeProvider.failure("gpu", "unreadable")),
                TextureAcquisitionChain::missing);

        TextureProvider.Result result = chain.acquire(request());

        assertEquals("missing", result.providerId());
        assertTrue(result.diagnostics().stream()
                .anyMatch(value -> value.code().equals("TEXTURE_READ_FAILED")));
        assertTrue(result.diagnostics().getFirst().message().contains("resource: missing"));
        assertTrue(result.diagnostics().getFirst().message().contains("gpu: unreadable"));
    }

    private static TextureProvider.Request request() {
        return new TextureProvider.Request(
                ResourceLocation.parse("test:runtime/texture"), null, null);
    }

    private static TextureProvider.Result extraction(String providerId) {
        return new TextureProvider.Result(
                providerId,
                new TextureKey(TextureKey.Kind.DYNAMIC,
                        "test:" + providerId,
                        "textures/generated/" + providerId + ".png"),
                new TextureImage(
                        1, 1,
                        new byte[] {(byte) 255, 0, (byte) 255, (byte) 255},
                        Optional.empty(), Optional.empty(), Optional.empty()),
                List.of());
    }

    private static final class FakeProvider implements TextureProvider {
        private final String id;
        private final Result result;
        private final IOException failure;
        private int calls;

        private FakeProvider(String id, Result result, IOException failure) {
            this.id = id;
            this.result = result;
            this.failure = failure;
        }

        static FakeProvider miss(String id) {
            return new FakeProvider(id, null, null);
        }

        static FakeProvider success(String id, Result result) {
            return new FakeProvider(id, result, null);
        }

        static FakeProvider failure(String id, String message) {
            return new FakeProvider(id, null, new IOException(message));
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Optional<Result> acquire(Request request) throws Exception {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return Optional.ofNullable(result);
        }
    }
}
