package com.nebysse.minetomesh.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nebysse.minetomesh.scene.Vec2f;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class AtlasSpriteResolverPolicyTest {
    @Test
    void derivesAtlasDimensionsFromCanonicalSpriteBounds() {
        AtlasSpriteIndex.Region region = AtlasSpriteResolver.region(
                Identifier.parse("create:block/casing_connected"),
                128, 128, 0.25F, 0.125F, 0.375F, 0.25F);

        assertEquals(1024, region.atlasWidth());
        assertEquals(1024, region.atlasHeight());
    }

    @Test
    void normalizesCreateTileInsideResolvedConnectedSheet() {
        List<Vec2f> normalized = AtlasSpriteResolver.normalize(
                List.of(new Vec2f(0.50F, 0.25F), new Vec2f(0.50F, 0.30F),
                        new Vec2f(0.55F, 0.30F), new Vec2f(0.55F, 0.25F)),
                new AtlasSpriteIndex.Region(
                        Identifier.parse("create:block/casing_connected"),
                        0.40F, 0.20F, 0.80F, 0.60F, 1024, 1024));

        assertEquals(0.25F, normalized.get(0).x(), 1.0E-6F);
        assertEquals(0.125F, normalized.get(0).y(), 1.0E-6F);
        assertEquals(0.375F, normalized.get(2).x(), 1.0E-6F);
        assertEquals(0.25F, normalized.get(2).y(), 1.0E-6F);
    }
}
