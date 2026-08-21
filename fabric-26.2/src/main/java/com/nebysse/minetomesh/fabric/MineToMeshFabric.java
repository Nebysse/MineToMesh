package com.nebysse.minetomesh.fabric;

import com.nebysse.minetomesh.MineToMeshInfo;
import com.nebysse.minetomesh.content.MineToMeshContent;
import com.nebysse.minetomesh.network.WandPayloads;
import com.nebysse.minetomesh.server.ServerExportSessions;
import com.nebysse.minetomesh.wand.WandInteractionHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class MineToMeshFabric implements ModInitializer {
    public static final String MOD_ID = MineToMeshInfo.MOD_ID;
    public static final String DISPLAY_NAME = MineToMeshInfo.DISPLAY_NAME;
    public static final String VERSION = "1.4.0-fabric-alpha.1";

    @Override
    public void onInitialize() {
        MineToMeshContent.register();
        WandPayloads.registerServer();
        ServerExportSessions.register();
        WandInteractionHandler.register();
        if (Boolean.getBoolean("minetomesh.serverSmoke")) {
            ServerLifecycleEvents.SERVER_STARTED.register(server -> {
                System.out.println("MINETOMESH_SERVER_READY");
                server.halt(false);
            });
        }
    }
}
