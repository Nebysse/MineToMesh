package com.onecuber.mcgltf.testmod.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;

public final class GpuResidentTexture extends AbstractTexture {
    @Override
    public void load(ResourceManager resourceManager) {
        TextureUtil.prepareImage(getId(), 2, 2);
        try (NativeImage image = new NativeImage(2, 2, false)) {
            image.setPixelRGBA(0, 0, 0xFF0000FF);
            image.setPixelRGBA(1, 0, 0xFF00FF00);
            image.setPixelRGBA(0, 1, 0xFFFF0000);
            image.setPixelRGBA(1, 1, 0xFFFFFFFF);
            image.upload(0, 0, 0, false);
        }
    }
}
