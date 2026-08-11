package com.onecuber.mcgltf;

import com.onecuber.mcgltf.content.McGltfContent;
import com.onecuber.mcgltf.network.WandPayloads;
import com.onecuber.mcgltf.network.WorkstationPayloads;
import com.onecuber.mcgltf.wand.WandInteractionHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(McGltf.MOD_ID)
public final class McGltf {
    public static final String MOD_ID = "mcgltf";
    public static final String DISPLAY_NAME = "MineToMesh";
    public static final String VERSION = "0.3.2";

    public McGltf(IEventBus modBus) {
        McGltfContent.register(modBus);
        WandPayloads.register(modBus);
        WorkstationPayloads.register(modBus);
        NeoForge.EVENT_BUS.addListener(WandInteractionHandler::onLeftClickBlock);
    }
}
