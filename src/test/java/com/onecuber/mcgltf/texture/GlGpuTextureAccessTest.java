package com.onecuber.mcgltf.texture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL13;

class GlGpuTextureAccessTest {
    @Test
    void restoresActiveUnitBindingAndPackAlignmentAfterSuccess() throws Exception {
        FakeGlApi gl = new FakeGlApi();
        gl.activeTexture = GL13.GL_TEXTURE3;
        gl.boundTexture = 91;
        gl.packAlignment = 8;
        GlGpuTextureAccess access = new GlGpuTextureAccess(gl);

        GpuTextureAccess.Pixels pixels = access.readRgba8(42);

        assertEquals(2, pixels.width());
        assertEquals(2, pixels.height());
        assertArrayEquals(gl.downloaded, pixels.rgba());
        assertEquals(GL13.GL_TEXTURE3, gl.activeTexture);
        assertEquals(91, gl.boundTexture);
        assertEquals(8, gl.packAlignment);
    }

    @Test
    void restoresStateWhenDownloadThrows() {
        FakeGlApi gl = new FakeGlApi();
        gl.failDownload = true;
        int originalUnit = gl.activeTexture;
        int originalBinding = gl.boundTexture;
        int originalPack = gl.packAlignment;

        assertThrows(IOException.class,
                () -> new GlGpuTextureAccess(gl).readRgba8(42));

        assertEquals(originalUnit, gl.activeTexture);
        assertEquals(originalBinding, gl.boundTexture);
        assertEquals(originalPack, gl.packAlignment);
    }

    private static final class FakeGlApi implements GlGpuTextureAccess.GlApi {
        private int activeTexture = GL13.GL_TEXTURE1;
        private int boundTexture = 17;
        private int packAlignment = 4;
        private boolean failDownload;
        private final byte[] downloaded = new byte[] {
                (byte) 255, 0, 0, (byte) 255,
                0, (byte) 255, 0, (byte) 255,
                0, 0, (byte) 255, (byte) 255,
                (byte) 255, (byte) 255, (byte) 255, (byte) 255
        };

        @Override
        public int activeTexture() {
            return activeTexture;
        }

        @Override
        public void activeTexture(int textureUnit) {
            activeTexture = textureUnit;
        }

        @Override
        public int boundTexture2d() {
            return boundTexture;
        }

        @Override
        public void bindTexture2d(int textureId) {
            boundTexture = textureId;
        }

        @Override
        public int packAlignment() {
            return packAlignment;
        }

        @Override
        public void packAlignment(int alignment) {
            packAlignment = alignment;
        }

        @Override
        public int levelWidth(int level) {
            return 2;
        }

        @Override
        public int levelHeight(int level) {
            return 2;
        }

        @Override
        public byte[] downloadRgba8(int width, int height) throws IOException {
            if (failDownload) {
                throw new IOException("forced download failure");
            }
            return downloaded.clone();
        }
    }
}
