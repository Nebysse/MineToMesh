package com.onecuber.mcgltf.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.onecuber.mcgltf.scene.Vec2f;
import org.junit.jupiter.api.Test;

class UvNormalizationTest {
    @Test
    void mapsAtlasRectangleToUnitCoordinates() {
        assertEquals(new Vec2f(0.0F, 0.0F),
                SpriteTextureExtractor.normalizeUv(0.25F, 0.5F, 0.25F, 0.5F, 0.75F, 1.0F));
        assertEquals(new Vec2f(1.0F, 1.0F),
                SpriteTextureExtractor.normalizeUv(0.75F, 1.0F, 0.25F, 0.5F, 0.75F, 1.0F));
        assertEquals(new Vec2f(0.5F, 0.5F),
                SpriteTextureExtractor.normalizeUv(0.5F, 0.75F, 0.25F, 0.5F, 0.75F, 1.0F));
    }

    @Test
    void rejectsDegenerateSpriteBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> SpriteTextureExtractor.normalizeUv(0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F));
    }
}
