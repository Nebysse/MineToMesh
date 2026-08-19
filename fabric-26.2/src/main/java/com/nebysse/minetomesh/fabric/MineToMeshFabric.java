package com.nebysse.minetomesh.fabric;

import com.nebysse.minetomesh.MineToMeshInfo;
import com.nebysse.minetomesh.content.MineToMeshContent;
import net.fabricmc.api.ModInitializer;

public final class MineToMeshFabric implements ModInitializer {
    public static final String MOD_ID = MineToMeshInfo.MOD_ID;
    public static final String DISPLAY_NAME = MineToMeshInfo.DISPLAY_NAME;
    public static final String VERSION = "1.2.0-fabric-alpha.1";

    @Override
    public void onInitialize() {
        MineToMeshContent.register();
    }
}
