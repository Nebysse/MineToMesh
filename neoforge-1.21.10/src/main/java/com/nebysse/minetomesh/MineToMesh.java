package com.nebysse.minetomesh;

import com.nebysse.minetomesh.content.MineToMeshContent;
import com.nebysse.minetomesh.network.WandPayloads;
import com.nebysse.minetomesh.server.ServerExportSessions;
import com.nebysse.minetomesh.wand.WandInteractionHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(MineToMesh.MOD_ID)
public final class MineToMesh {
    public static final String MOD_ID = "minetomesh";
    public static final String DISPLAY_NAME = "MineToMesh";
    public static final String VERSION = "1.5.0";

    public MineToMesh(IEventBus modBus) {
        MineToMeshContent.register(modBus);
        WandPayloads.register(modBus);
        ServerExportSessions.register();
        NeoForge.EVENT_BUS.addListener(WandInteractionHandler::onLeftClickBlock);
    }
}
