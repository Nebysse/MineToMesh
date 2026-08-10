package com.onecuber.mcgltf;

import com.onecuber.mcgltf.content.McGltfContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(McGltf.MOD_ID)
public final class McGltf {
    public static final String MOD_ID = "mcgltf";
    public static final String DISPLAY_NAME = "MineToMesh";
    public static final String VERSION = "0.3.0";

    public McGltf(IEventBus modBus) {
        McGltfContent.register(modBus);
    }
}
