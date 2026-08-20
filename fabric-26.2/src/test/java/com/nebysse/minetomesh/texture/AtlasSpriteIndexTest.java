package com.nebysse.minetomesh.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nebysse.minetomesh.scene.Vec2f;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class AtlasSpriteIndexTest {
    private static final Identifier ORIGINAL =
            Identifier.parse("create:block/andesite_casing");
    private static final Identifier CONNECTED =
            Identifier.parse("create:block/andesite_casing_connected");

    @Test
    void keepsDeclaredSpriteWhenItContainsAllUvs() {
        AtlasSpriteIndex index = new AtlasSpriteIndex(List.of(
                region(ORIGINAL, 0.10F, 0.10F, 0.20F, 0.20F)));

        AtlasSpriteIndex.Resolution result = index.resolve(
                ORIGINAL, quad(0.11F, 0.11F, 0.19F, 0.19F));

        assertEquals(AtlasSpriteIndex.Kind.DECLARED, result.kind());
        assertEquals(ORIGINAL, result.region().orElseThrow().id());
    }

    @Test
    void redirectsCreateStyleUvsToConnectedSheet() {
        AtlasSpriteIndex index = new AtlasSpriteIndex(List.of(
                region(ORIGINAL, 0.10F, 0.10F, 0.20F, 0.20F),
                region(CONNECTED, 0.40F, 0.30F, 0.80F, 0.70F)));

        AtlasSpriteIndex.Resolution result = index.resolve(
                ORIGINAL, quad(0.45F, 0.35F, 0.50F, 0.40F));

        assertEquals(AtlasSpriteIndex.Kind.REDIRECTED, result.kind());
        assertEquals(CONNECTED, result.region().orElseThrow().id());
    }

    @Test
    void selectsSmallestCoveringRegionThenStableId() {
        Identifier broad = Identifier.parse("test:broad");
        Identifier zed = Identifier.parse("test:zed");
        Identifier alpha = Identifier.parse("test:alpha");
        AtlasSpriteIndex index = new AtlasSpriteIndex(List.of(
                region(broad, 0.0F, 0.0F, 1.0F, 1.0F),
                region(zed, 0.4F, 0.4F, 0.6F, 0.6F),
                region(alpha, 0.4F, 0.4F, 0.6F, 0.6F)));

        AtlasSpriteIndex.Resolution result = index.resolve(
                ORIGINAL, quad(0.45F, 0.45F, 0.55F, 0.55F));

        assertEquals(alpha, result.region().orElseThrow().id());
    }

    @Test
    void returnsDeclaredFallbackWhenNoRegionCoversUvs() {
        AtlasSpriteIndex index = new AtlasSpriteIndex(List.of(
                region(ORIGINAL, 0.10F, 0.10F, 0.20F, 0.20F)));

        AtlasSpriteIndex.Resolution result = index.resolve(
                ORIGINAL, quad(0.80F, 0.80F, 0.90F, 0.90F));

        assertEquals(AtlasSpriteIndex.Kind.FALLBACK, result.kind());
        assertEquals(ORIGINAL, result.region().orElseThrow().id());
    }

    @Test
    void halfPixelToleranceKeepsUvsOnDeclaredBoundary() {
        AtlasSpriteIndex index = new AtlasSpriteIndex(List.of(
                new AtlasSpriteIndex.Region(ORIGINAL,
                        0.10F, 0.10F, 0.20F, 0.20F, 1024, 1024)));

        AtlasSpriteIndex.Resolution result = index.resolve(
                ORIGINAL, quad(0.10F - 0.25F / 1024F, 0.10F,
                        0.20F + 0.25F / 1024F, 0.20F));

        assertEquals(AtlasSpriteIndex.Kind.DECLARED, result.kind());
        assertTrue(result.region().isPresent());
    }

    @Test
    void rejectsEmptyUvSets() {
        AtlasSpriteIndex index = new AtlasSpriteIndex(List.of(
                region(ORIGINAL, 0.10F, 0.10F, 0.20F, 0.20F)));

        assertThrows(IllegalArgumentException.class,
                () -> index.resolve(ORIGINAL, List.of()));
    }

    private static AtlasSpriteIndex.Region region(
            Identifier id, float u0, float v0, float u1, float v1) {
        return new AtlasSpriteIndex.Region(id, u0, v0, u1, v1, 1024, 1024);
    }

    private static List<Vec2f> quad(float u0, float v0, float u1, float v1) {
        return List.of(new Vec2f(u0, v0), new Vec2f(u0, v1),
                new Vec2f(u1, v1), new Vec2f(u1, v0));
    }
}
