package com.nebysse.minetomesh.testmod.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.renderer.texture.AbstractTexture;

public final class GpuResidentTexture extends AbstractTexture {
    public GpuResidentTexture() {
        // 1.21.10 的纹理经 blaze3d GpuDevice 分配；像素打包为 ARGB。
        try (NativeImage image = new NativeImage(2, 2, false)) {
            image.setPixel(0, 0, 0xFFFF0000);
            image.setPixel(1, 0, 0xFF00FF00);
            image.setPixel(0, 1, 0xFF0000FF);
            image.setPixel(1, 1, 0xFFFFFFFF);
            GpuDevice device = RenderSystem.getDevice();
            this.texture = device.createTexture(
                    "minetomesh_test/gpu_only", 5, TextureFormat.RGBA8, 2, 2, 1, 1);
            this.textureView = device.createTextureView(this.texture);
            device.createCommandEncoder().writeToTexture(this.texture, image);
        }
    }
}
