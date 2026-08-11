package com.nebysse.minetomesh.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nebysse.minetomesh.scene.TextureKey;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextureRegistryTest {
    @TempDir
    Path tempDir;

    @Test
    void deduplicatesByStableIdentityRatherThanPayloadBytes() {
        TextureRegistry registry = new TextureRegistry();
        TextureKey stone = key("stone");
        TextureKey duplicateBytes = key("stone_copy");
        TextureImage red = image(255, 0, 0, 255);

        assertEquals(0, registry.register(stone, red));
        assertEquals(0, registry.register(stone, image(0, 255, 0, 255)));
        assertEquals(1, registry.register(duplicateBytes, red));
        assertEquals(2, registry.size());
    }

    @Test
    void writesRgbaPayloadAsPngUnderTheExportRoot() throws Exception {
        TextureRegistry registry = new TextureRegistry();
        registry.register(key("stone"), image(12, 34, 56, 78));

        registry.writeAll(tempDir);

        Path output = tempDir.resolve("textures/minecraft/block/stone.png");
        assertTrue(Files.isRegularFile(output));
        BufferedImage decoded = ImageIO.read(output.toFile());
        assertEquals(0x4E0C2238, decoded.getRGB(0, 0));
    }

    @Test
    void textureImagesDefensivelyCopyPayloadsAndAnimationLists() {
        byte[] rgba = {1, 2, 3, 4};
        TextureImage image = new TextureImage(1, 1, rgba, Optional.empty(), Optional.empty(),
                Optional.of(new TextureImage.AnimationInfo(1, 1,
                        java.util.List.of(0, 1), java.util.List.of(2, 3), true)));
        rgba[0] = 99;
        byte[] returned = image.rgba();
        returned[1] = 99;

        assertEquals(1, image.rgba()[0]);
        assertEquals(2, image.rgba()[1]);
        assertEquals(java.util.List.of(0, 1), image.animation().orElseThrow().frameOrder());
    }

    private static TextureKey key(String name) {
        return new TextureKey(TextureKey.Kind.ATLAS_SPRITE,
                "minecraft:block/" + name,
                "textures/minecraft/block/" + name + ".png");
    }

    private static TextureImage image(int red, int green, int blue, int alpha) {
        return new TextureImage(1, 1,
                new byte[] {(byte) red, (byte) green, (byte) blue, (byte) alpha},
                Optional.empty(), Optional.empty(), Optional.empty());
    }
}
