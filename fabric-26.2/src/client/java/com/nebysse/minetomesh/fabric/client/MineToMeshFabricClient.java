package com.nebysse.minetomesh.fabric.client;

import com.nebysse.minetomesh.client.MineToMeshClient;
import com.nebysse.minetomesh.job.DefaultExportPipeline;
import com.nebysse.minetomesh.network.ExportWandGrantedPayload;
import com.nebysse.minetomesh.network.ExportWandRejectedPayload;
import com.nebysse.minetomesh.network.WandClientReceiver;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public final class MineToMeshFabricClient implements ClientModInitializer {
    private MineToMeshClient client;

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
        client = new MineToMeshClient(
                (selection, name, options, telemetry) ->
                        DefaultExportPipeline.create(
                                net.minecraft.client.Minecraft.getInstance(),
                                selection, name, options, telemetry),
                (selection, name) -> DefaultExportPipeline.create(
                        net.minecraft.client.Minecraft.getInstance(),
                        selection, name));
        client.register();
    }
}
