package com.onecuber.mcgltf.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.onecuber.mcgltf.scene.TextureKey;
import org.junit.jupiter.api.Test;

class BlockPrimitiveRouterTest {
    @Test
    void routesOnlyVanillaGrassSideOverlayGlobally() {
        assertEquals(BlockPrimitiveRouter.Route.GLOBAL_GRASS_SIDE_OVERLAY,
                BlockPrimitiveRouter.route(key(
                        "minecraft:block/grass_block_side_overlay")));
        assertEquals(BlockPrimitiveRouter.Route.SECTION,
                BlockPrimitiveRouter.route(key(
                        "minecraft:block/grass_block_side")));
        assertEquals(BlockPrimitiveRouter.Route.SECTION,
                BlockPrimitiveRouter.route(key(
                        "other:block/grass_block_side_overlay")));
    }

    private static TextureKey key(String id) {
        return new TextureKey(TextureKey.Kind.ATLAS_SPRITE, id,
                "textures/" + id.replace(':', '/') + ".png");
    }
}
