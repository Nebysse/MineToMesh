package com.onecuber.mcgltf.capture;

import com.onecuber.mcgltf.scene.TextureKey;
import java.util.Objects;

public final class BlockPrimitiveRouter {
    public static final String GRASS_SIDE_OVERLAY_ID =
            "minecraft:block/grass_block_side_overlay";
    public static final String OVERLAY_OBJECT_NAME =
            "selection/grass_side_overlay";

    private BlockPrimitiveRouter() {
    }

    public static Route route(TextureKey texture) {
        Objects.requireNonNull(texture, "texture");
        return texture.sourceId().equals(GRASS_SIDE_OVERLAY_ID)
                ? Route.GLOBAL_GRASS_SIDE_OVERLAY
                : Route.SECTION;
    }

    public enum Route {
        SECTION,
        GLOBAL_GRASS_SIDE_OVERLAY
    }
}
