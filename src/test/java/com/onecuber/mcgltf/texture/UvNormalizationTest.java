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
    void declaredSpriteBoundsExposeCreateRedirectAsOutOfRangeUvs() {
        Vec2f normalized = SpriteTextureExtractor.normalizeUv(
                0.50F, 0.30F,
                0.10F, 0.10F, 0.20F, 0.20F);

        assertEquals(4.0F, normalized.x(), 1.0E-6F);
        assertEquals(2.0F, normalized.y(), 1.0E-6F);
    }

    @Test
    void rejectsDegenerateSpriteBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> SpriteTextureExtractor.normalizeUv(0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F));
    }
}
