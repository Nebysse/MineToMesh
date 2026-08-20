package com.nebysse.minetomesh.fabric.client;

import com.nebysse.minetomesh.client.MineToMeshClient;
import com.nebysse.minetomesh.job.DefaultExportPipeline;
import com.nebysse.minetomesh.network.BatchLoadStartedPayload;
import com.nebysse.minetomesh.network.BatchReadyPayload;
import com.nebysse.minetomesh.network.ExportCancelAcknowledgedPayload;
import com.nebysse.minetomesh.network.ExportSessionAcceptedPayload;
import com.nebysse.minetomesh.network.ExportSessionFailedPayload;
import com.nebysse.minetomesh.network.ExportSessionFinishedPayload;
import com.nebysse.minetomesh.network.ExportSessionRejectedPayload;
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
        ClientPlayNetworking.registerGlobalReceiver(ExportSessionAcceptedPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> WandClientReceiver.receiveSession(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ExportSessionRejectedPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> WandClientReceiver.receiveSession(payload)));
        ClientPlayNetworking.registerGlobalReceiver(BatchLoadStartedPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> WandClientReceiver.receiveSession(payload)));
        ClientPlayNetworking.registerGlobalReceiver(BatchReadyPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> WandClientReceiver.receiveSession(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ExportCancelAcknowledgedPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> WandClientReceiver.receiveSession(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ExportSessionFinishedPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> WandClientReceiver.receiveSession(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ExportSessionFailedPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> WandClientReceiver.receiveSession(payload)));
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
