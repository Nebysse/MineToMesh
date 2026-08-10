package com.onecuber.mcgltf.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class TextureProviderFactoryTest {
    @Test
    void exposesResourceAndDynamicProvidersInStableOrder() {
        assertEquals("resource", ResourceTextureExtractor.resourceProvider().id());
        assertEquals("dynamic", ResourceTextureExtractor.dynamicProvider().id());
    }

    @Test
    void disposedDynamicTextureFallsThroughToGpuProvider() throws Exception {
        DynamicTexture disposed = new DynamicTexture(new NativeImage(1, 1, false));
        disposed.close();
        TextureProvider.Request request = new TextureProvider.Request(
                ResourceLocation.parse("test:runtime/disposed"),
                null,
                new FakeTextureManager(disposed));

        assertTrue(ResourceTextureExtractor.dynamicProvider()
                .acquire(request).isEmpty());
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
