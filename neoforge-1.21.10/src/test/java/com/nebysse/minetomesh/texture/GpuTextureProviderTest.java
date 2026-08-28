package com.nebysse.minetomesh.texture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nebysse.minetomesh.scene.TextureKey;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class GpuTextureProviderTest {
    @Test
    void hashesGpuPixelsAndRecordsInformationalDiagnostic() throws Exception {
        byte[] rgba = new byte[] {
                1, 2, 3, (byte) 255,
                4, 5, 6, (byte) 255
        };
        // 1.21.10 的 TextureManager 构造会立刻创建贴图并访问 GPU 设备，
        // 无头测试改为注入纹理解析器，不再实例化 TextureManager。
        AbstractTexture texture = new AbstractTexture() {
        };
        GpuTextureProvider provider = new GpuTextureProvider(
                textureId -> new GpuTextureAccess.Pixels(2, 1, rgba),
                ignored -> 42,
                request -> texture);
        ResourceLocation id = ResourceLocation.parse("test:runtime/gpu");
        TextureProvider.Request request = new TextureProvider.Request(
                id, null, null);
        TextureProvider.Result result = provider.acquire(request).orElseThrow();

        assertEquals("gpu", result.providerId());
        assertEquals(TextureKey.Kind.DYNAMIC, result.key().kind());
        assertTrue(result.key().outputPath().matches(
                "textures/generated/[0-9a-f]{16}\\.png"));
        assertArrayEquals(rgba, result.image().rgba());
        assertTrue(result.diagnostics().stream().anyMatch(
                value -> value.code().equals("GPU_TEXTURE_READBACK_USED")));
    }
}
