package com.nebysse.minetomesh.texture;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.util.Objects;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public final class GlGpuTextureAccess implements GpuTextureAccess {
    private final GlApi gl;
    private final boolean enforceRenderThread;

    public GlGpuTextureAccess() {
        this(new MojangGlApi(), true);
    }

    GlGpuTextureAccess(GlApi gl) {
        this(gl, false);
    }

    private GlGpuTextureAccess(GlApi gl, boolean enforceRenderThread) {
        this.gl = Objects.requireNonNull(gl, "gl");
        this.enforceRenderThread = enforceRenderThread;
    }

    @Override
    public Pixels readRgba8(int textureId) throws IOException {
        if (textureId <= 0) {
            throw new IOException("Texture ID is not allocated: " + textureId);
        }
        if (enforceRenderThread) {
            RenderSystem.assertOnRenderThreadOrInit();
        }
        int originalActive = gl.activeTexture();
        gl.activeTexture(GL13.GL_TEXTURE0);
        int originalBinding = gl.boundTexture2d();
        int originalPack = gl.packAlignment();
        try {
            gl.bindTexture2d(textureId);
            gl.packAlignment(1);
            int width = gl.levelWidth(0);
            int height = gl.levelHeight(0);
            if (width <= 0 || height <= 0) {
                throw new IOException(
                        "Texture level 0 has invalid dimensions: " + width + "x" + height);
            }
            return new Pixels(width, height, gl.downloadRgba8(width, height));
        } finally {
            gl.bindTexture2d(originalBinding);
            gl.packAlignment(originalPack);
            gl.activeTexture(originalActive);
        }
    }

    interface GlApi {
        int activeTexture();

        void activeTexture(int textureUnit);

        int boundTexture2d();

        void bindTexture2d(int textureId);

        int packAlignment();

        void packAlignment(int alignment);

        int levelWidth(int level);

        int levelHeight(int level);

        byte[] downloadRgba8(int width, int height) throws IOException;
    }

    private static final class MojangGlApi implements GlApi {
        @Override
        public int activeTexture() {
            return GlStateManager._getActiveTexture();
        }

        @Override
        public void activeTexture(int textureUnit) {
            GlStateManager._activeTexture(textureUnit);
        }

        @Override
        public int boundTexture2d() {
            return GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
        }

        @Override
        public void bindTexture2d(int textureId) {
            GlStateManager._bindTexture(textureId);
        }

        @Override
        public int packAlignment() {
            return GlStateManager._getInteger(GL11.GL_PACK_ALIGNMENT);
        }

        @Override
        public void packAlignment(int alignment) {
            GlStateManager._pixelStore(GL11.GL_PACK_ALIGNMENT, alignment);
        }

        @Override
        public int levelWidth(int level) {
            return GlStateManager._getTexLevelParameter(
                    GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_WIDTH);
        }

        @Override
        public int levelHeight(int level) {
            return GlStateManager._getTexLevelParameter(
                    GL11.GL_TEXTURE_2D, level, GL11.GL_TEXTURE_HEIGHT);
        }

        @Override
        public byte[] downloadRgba8(int width, int height) {
            try (NativeImage image = new NativeImage(width, height, false)) {
                image.downloadTexture(0, false);
                return ResourceTextureExtractor.copyRgba(image);
            }
        }
    }
}
