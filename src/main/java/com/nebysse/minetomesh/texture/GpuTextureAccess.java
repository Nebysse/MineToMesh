package com.nebysse.minetomesh.texture;

import java.io.IOException;

public interface GpuTextureAccess {
    Pixels readRgba8(int textureId) throws IOException;

    record Pixels(int width, int height, byte[] rgba) {
        public Pixels {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Texture dimensions must be positive");
            }
            rgba = rgba.clone();
            int expected = Math.multiplyExact(Math.multiplyExact(width, height), 4);
            if (rgba.length != expected) {
                throw new IllegalArgumentException(
                        "RGBA byte length does not match dimensions");
            }
        }

        @Override
        public byte[] rgba() {
            return rgba.clone();
        }
    }
}
