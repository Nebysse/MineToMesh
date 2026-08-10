package com.onecuber.mcgltf.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
        DynamicTexture disposed = allocateWithoutConstructor(DynamicTexture.class);
        TextureProvider.Request request = new TextureProvider.Request(
                ResourceLocation.parse("test:runtime/disposed"),
                null,
                new FakeTextureManager(disposed));

        assertTrue(ResourceTextureExtractor.dynamicProvider()
                .acquire(request).isEmpty());
    }

    private static <T> T allocateWithoutConstructor(Class<T> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field field = unsafeType.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        Method allocateInstance = unsafeType.getMethod("allocateInstance", Class.class);
        return type.cast(allocateInstance.invoke(unsafe, type));
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
