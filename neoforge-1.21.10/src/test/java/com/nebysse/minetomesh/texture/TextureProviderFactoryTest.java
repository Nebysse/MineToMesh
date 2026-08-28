package com.nebysse.minetomesh.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
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
        // 1.21.10 的 TextureManager 构造会立刻访问 GPU 设备，无头测试不可用；
        // 注入纹理解析器，直接给 dynamic provider 一个未构造的（像素为空的）DynamicTexture。
        DynamicTexture disposed = allocateWithoutConstructor(DynamicTexture.class);
        TextureProvider.Request request = new TextureProvider.Request(
                ResourceLocation.parse("test:runtime/disposed"),
                null,
                null);

        assertTrue(ResourceTextureExtractor.dynamicProvider(
                        ignored -> disposed)
                .acquire(request).isEmpty());
    }

    @Test
    void missingTextureResolverFallsThroughToLaterProviders() throws Exception {
        TextureProvider.Request request = new TextureProvider.Request(
                ResourceLocation.parse("test:runtime/missing"),
                null,
                null);

        assertTrue(ResourceTextureExtractor.dynamicProvider(
                        ignored -> null)
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
}
