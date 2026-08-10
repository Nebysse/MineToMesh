package com.onecuber.mcgltf.network;

import com.onecuber.mcgltf.McGltf;
import com.onecuber.mcgltf.workstation.Endpoint;
import com.onecuber.mcgltf.workstation.ExportWorkstationBlockEntity;
import com.onecuber.mcgltf.workstation.ExportWorkstationMenu;
import com.onecuber.mcgltf.workstation.WorkstationCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class WorkstationPayloads {
    private WorkstationPayloads() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(WorkstationPayloads::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(UpdateCoordinatePayload.TYPE,
                UpdateCoordinatePayload.STREAM_CODEC,
                WorkstationPayloads::handleUpdateCoordinate);
        registrar.playToServer(CaptureFeetPayload.TYPE,
                CaptureFeetPayload.STREAM_CODEC,
                WorkstationPayloads::handleCaptureFeet);
        registrar.playToServer(ExportRequestPayload.TYPE,
                ExportRequestPayload.STREAM_CODEC,
                WorkstationPayloads::handleExportRequest);
        registrar.playToClient(ExportGrantedPayload.TYPE, ExportGrantedPayload.STREAM_CODEC,
                (payload, context) -> WorkstationClientReceiver.receive(payload));
        registrar.playToClient(ExportRejectedPayload.TYPE, ExportRejectedPayload.STREAM_CODEC,
                (payload, context) -> WorkstationClientReceiver.receive(payload));
    }

    private static void handleUpdateCoordinate(
            UpdateCoordinatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = serverPlayer(context);
            if (player == null) {
                return;
            }
            ExportWorkstationBlockEntity station = validStation(player, payload.stationPos());
            if (station == null) {
                return;
            }
            station.setCoordinates(station.coordinates().with(
                    payload.endpoint(), payload.axis(), payload.value()));
        });
    }

    private static void handleCaptureFeet(CaptureFeetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = serverPlayer(context);
            if (player == null) {
                return;
            }
            ExportWorkstationBlockEntity station = validStation(player, payload.stationPos());
            if (station == null) {
                return;
            }
            BlockPos feet = player.blockPosition().below();
            station.setCoordinates(station.coordinates()
                    .withEndpoint(payload.endpoint(), feet));
        });
    }

    private static void handleExportRequest(
            ExportRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = serverPlayer(context);
            if (player == null) {
                return;
            }
            ExportWorkstationBlockEntity station = validStation(player, payload.stationPos());
            if (station == null) {
                return;
            }
            WorkstationRequestPolicy.Validation permissionValidation =
                    WorkstationRequestPolicy.validateExportPermission(
                            player.getServer().isSingleplayer(),
                            player.createCommandSourceStack().hasPermission(2));
            if (!permissionValidation.accepted()) {
                sendReject(player, payload.stationPos(), permissionValidation.reasonKey());
                return;
            }
            WorkstationRequestPolicy.Validation nameValidation =
                    WorkstationRequestPolicy.validateExportName(payload.exportName());
            if (!nameValidation.accepted()) {
                sendReject(player, payload.stationPos(), nameValidation.reasonKey());
                return;
            }
            WorkstationCoordinates coordinates = station.coordinates();
            WorkstationRequestPolicy.Validation boundsValidation =
                    WorkstationRequestPolicy.validateCoordinates(
                            coordinates, player.level().getMinBuildHeight(),
                            player.level().getMaxBuildHeight());
            if (!boundsValidation.accepted()) {
                sendReject(player, payload.stationPos(), boundsValidation.reasonKey());
                return;
            }
            PacketDistributor.sendToPlayer(player, new ExportGrantedPayload(
                    payload.stationPos(),
                    payload.exportName(),
                    coordinates.first(),
                    coordinates.second(),
                    player.level().dimension().location().toString()));
        });
    }

    private static ExportWorkstationBlockEntity validStation(
            ServerPlayer player, BlockPos stationPos) {
        if (!(player.containerMenu instanceof ExportWorkstationMenu menu)) {
            sendReject(player, stationPos, "mcgltf.error.workstation.no_menu");
            return null;
        }
        WorkstationRequestPolicy.Validation identity =
                WorkstationRequestPolicy.validateMenuIdentity(
                        stationPos, menu.stationPos());
        if (!identity.accepted()) {
            sendReject(player, stationPos, identity.reasonKey());
            return null;
        }
        if (!(player.level().getBlockEntity(stationPos)
                instanceof ExportWorkstationBlockEntity station)) {
            sendReject(player, stationPos, "mcgltf.error.workstation.missing_station");
            return null;
        }
        if (!ExportWorkstationMenu.isValidStation(
                player.position(), stationPos, true)) {
            sendReject(player, stationPos, "mcgltf.error.workstation.too_far");
            return null;
        }
        return station;
    }

    private static void sendReject(
            ServerPlayer player, BlockPos stationPos, String reasonKey) {
        PacketDistributor.sendToPlayer(player,
                new ExportRejectedPayload(stationPos, reasonKey));
    }

    private static ServerPlayer serverPlayer(IPayloadContext context) {
        return context.player() instanceof ServerPlayer player ? player : null;
    }
}
