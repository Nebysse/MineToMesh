package com.nebysse.minetomesh.texture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nebysse.minetomesh.scene.TextureKey;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

class GpuTextureProviderTest {
    @Test
    void hashesGpuPixelsAndRecordsInformationalDiagnostic() throws Exception {
        byte[] rgba = new byte[] {
                1, 2, 3, (byte) 255,
                4, 5, 6, (byte) 255
        };
        GpuTextureProvider provider = new GpuTextureProvider(
                textureId -> new GpuTextureAccess.Pixels(2, 1, rgba),
                texture -> 42);
        ResourceLocation id = ResourceLocation.parse("test:runtime/gpu");
        TextureProvider.Request request = new TextureProvider.Request(
                id, null, new FakeTextureManager(new AllocatedTexture(42)));
        TextureProvider.Result result = provider.acquire(request).orElseThrow();

        assertEquals("gpu", result.providerId());
        assertEquals(TextureKey.Kind.DYNAMIC, result.key().kind());
        assertTrue(result.key().outputPath().matches(
                "textures/generated/[0-9a-f]{16}\\.png"));
        assertArrayEquals(rgba, result.image().rgba());
        assertTrue(result.diagnostics().stream().anyMatch(
                value -> value.code().equals("GPU_TEXTURE_READBACK_USED")));
    }

    private static final class AllocatedTexture extends AbstractTexture {
        private AllocatedTexture(int id) {
            this.id = id;
        }

        @Override
        public void load(ResourceManager resourceManager) {
        }
    }

    private static final class FakeTextureManager extends TextureManager {
        private final AbstractTexture texture;

        private FakeTextureManager(AbstractTexture texture) {
            super(null);
            this.texture = texture;
        }

        @Override
        public AbstractTexture getTexture(ResourceLocation location) {
            return texture;
        }
    }
}
