package com.nebysse.minetomesh.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class MineToMeshFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Client services are registered by the following implementation tasks.
    }
}
