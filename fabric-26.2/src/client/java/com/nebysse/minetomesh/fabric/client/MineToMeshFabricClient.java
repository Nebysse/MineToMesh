package com.nebysse.minetomesh.fabric.client;

import com.nebysse.minetomesh.network.ExportWandGrantedPayload;
import com.nebysse.minetomesh.network.ExportWandRejectedPayload;
import com.nebysse.minetomesh.network.WandClientReceiver;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public final class MineToMeshFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                ExportWandGrantedPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> WandClientReceiver.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(
                ExportWandRejectedPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> WandClientReceiver.receive(payload)));
    }
}
