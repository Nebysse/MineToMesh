package com.nebysse.minetomesh.texture;

import java.io.IOException;
import java.util.Objects;

/**
 * Compatibility readback adapter. The injectable GL facade remains available
 * for deterministic tests; the 26.2 production renderer uses an opaque GPU
 * backend and therefore reports this provider as unavailable instead of
 * relying on OpenGL-only state.
 */
public final class GlGpuTextureAccess implements GpuTextureAccess {
    private final GlApi gl;

    public GlGpuTextureAccess() {
        this(new UnavailableGlApi());
    }

    GlGpuTextureAccess(GlApi gl) {
        this.gl = Objects.requireNonNull(gl, "gl");
    }

    @Override
    public Pixels readRgba8(int textureId) throws IOException {
        if (textureId <= 0) {
            throw new IOException("Texture ID is not allocated: " + textureId);
        }
        int originalActive = gl.activeTexture();
        gl.activeTexture(0);
        int originalBinding = gl.boundTexture2d();
        int originalPack = gl.packAlignment();
        try {
            gl.bindTexture2d(textureId);
            gl.packAlignment(1);
            int width = gl.levelWidth(0);
            int height = gl.levelHeight(0);
            if (width <= 0 || height <= 0) {
                throw new IOException(
                        "Texture level 0 has invalid dimensions: "
                                + width + "x" + height);
            }
            return new Pixels(width, height, gl.downloadRgba8(width, height));
        } finally {
            gl.bindTexture2d(originalBinding);
            gl.packAlignment(originalPack);
            gl.activeTexture(originalActive);
        }
    }

    interface GlApi {
        int activeTexture() throws IOException;

        void activeTexture(int textureUnit) throws IOException;

        int boundTexture2d() throws IOException;

        void bindTexture2d(int textureId) throws IOException;

        int packAlignment() throws IOException;

        void packAlignment(int alignment) throws IOException;

        int levelWidth(int level) throws IOException;

        int levelHeight(int level) throws IOException;

        byte[] downloadRgba8(int width, int height) throws IOException;
    }

    private static final class UnavailableGlApi implements GlApi {
        private static IOException unavailable() {
            return new IOException(
                    "Minecraft 26.2 GPU backend exposes no OpenGL texture ID");
        }

        @Override
        public int activeTexture() throws IOException {
            throw unavailable();
        }

        @Override
        public void activeTexture(int textureUnit) throws IOException {
            throw unavailable();
        }

        @Override
        public int boundTexture2d() throws IOException {
            throw unavailable();
        }

        @Override
        public void bindTexture2d(int textureId) throws IOException {
            throw unavailable();
        }

        @Override
        public int packAlignment() throws IOException {
            throw unavailable();
        }

        @Override
        public void packAlignment(int alignment) throws IOException {
            throw unavailable();
        }

        @Override
        public int levelWidth(int level) throws IOException {
            throw unavailable();
        }

        @Override
        public int levelHeight(int level) throws IOException {
            throw unavailable();
        }

        @Override
        public byte[] downloadRgba8(int width, int height) throws IOException {
            throw unavailable();
        }
    }
}
