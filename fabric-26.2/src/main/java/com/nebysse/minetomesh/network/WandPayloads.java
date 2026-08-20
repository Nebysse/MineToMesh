package com.nebysse.minetomesh.network;

import com.nebysse.minetomesh.content.MineToMeshContent;
import com.nebysse.minetomesh.output.ExportName;
import com.nebysse.minetomesh.wand.ExportWandMenu;
import com.nebysse.minetomesh.wand.ExportWandSelection;
import com.nebysse.minetomesh.wand.ExportWandService;
import com.nebysse.minetomesh.wand.WandAirTarget;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;

public final class WandPayloads {
    private WandPayloads() {
    }

    public static void registerServer() {
        PayloadTypeRegistry.serverboundPlay().register(
                ClearWandSelectionPayload.TYPE, ClearWandSelectionPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                SetWandAirEndpointPayload.TYPE, SetWandAirEndpointPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                UpdateWandEndpointPayload.TYPE, UpdateWandEndpointPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                ToggleWandOverlayPayload.TYPE, ToggleWandOverlayPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                ToggleWandIncludePlayersPayload.TYPE,
                ToggleWandIncludePlayersPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                UpdateWandBatchSizePayload.TYPE,
                UpdateWandBatchSizePayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                UpdateWandExportNamePayload.TYPE, UpdateWandExportNamePayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                ExportWandRequestPayload.TYPE, ExportWandRequestPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                ExportWandGrantedPayload.TYPE, ExportWandGrantedPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                ExportWandRejectedPayload.TYPE, ExportWandRejectedPayload.STREAM_CODEC);
        registerRollingSessionTypes();

        ServerPlayNetworking.registerGlobalReceiver(
                ClearWandSelectionPayload.TYPE, WandPayloads::handleClearSelection);
        ServerPlayNetworking.registerGlobalReceiver(
                SetWandAirEndpointPayload.TYPE, WandPayloads::handleAirEndpoint);
        ServerPlayNetworking.registerGlobalReceiver(
                UpdateWandEndpointPayload.TYPE, WandPayloads::handleUpdateEndpoint);
        ServerPlayNetworking.registerGlobalReceiver(
                ToggleWandOverlayPayload.TYPE, WandPayloads::handleToggleOverlay);
        ServerPlayNetworking.registerGlobalReceiver(
                ToggleWandIncludePlayersPayload.TYPE,
                WandPayloads::handleToggleIncludePlayers);
        ServerPlayNetworking.registerGlobalReceiver(
                UpdateWandBatchSizePayload.TYPE,
                WandPayloads::handleUpdateBatchSize);
        ServerPlayNetworking.registerGlobalReceiver(
                UpdateWandExportNamePayload.TYPE, WandPayloads::handleUpdateExportName);
        ServerPlayNetworking.registerGlobalReceiver(
                ExportWandRequestPayload.TYPE, WandPayloads::handleExportRequest);
        ServerPlayNetworking.registerGlobalReceiver(BatchClientReadablePayload.TYPE,
                (payload, context) -> WandServerSessionReceiver.receive(payload, context.player()));
        ServerPlayNetworking.registerGlobalReceiver(BatchCaptureCompletedPayload.TYPE,
                (payload, context) -> WandServerSessionReceiver.receive(payload, context.player()));
        ServerPlayNetworking.registerGlobalReceiver(ExportProgressHeartbeatPayload.TYPE,
                (payload, context) -> WandServerSessionReceiver.receive(payload, context.player()));
        ServerPlayNetworking.registerGlobalReceiver(CancelExportRequestPayload.TYPE,
                (payload, context) -> WandServerSessionReceiver.receive(payload, context.player()));
        ServerPlayNetworking.registerGlobalReceiver(ExportClientCompletedPayload.TYPE,
                (payload, context) -> WandServerSessionReceiver.receive(payload, context.player()));
    }

    private static void registerRollingSessionTypes() {
        PayloadTypeRegistry.serverboundPlay().register(BatchClientReadablePayload.TYPE,
                BatchClientReadablePayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BatchCaptureCompletedPayload.TYPE,
                BatchCaptureCompletedPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ExportProgressHeartbeatPayload.TYPE,
                ExportProgressHeartbeatPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CancelExportRequestPayload.TYPE,
                CancelExportRequestPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ExportClientCompletedPayload.TYPE,
                ExportClientCompletedPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ExportSessionAcceptedPayload.TYPE,
                ExportSessionAcceptedPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ExportSessionRejectedPayload.TYPE,
                ExportSessionRejectedPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BatchLoadStartedPayload.TYPE,
                BatchLoadStartedPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BatchReadyPayload.TYPE,
                BatchReadyPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ExportCancelAcknowledgedPayload.TYPE,
                ExportCancelAcknowledgedPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ExportSessionFinishedPayload.TYPE,
                ExportSessionFinishedPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ExportSessionFailedPayload.TYPE,
                ExportSessionFailedPayload.STREAM_CODEC);
    }

    private static void handleClearSelection(
            ClearWandSelectionPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        ItemStack stack = player.getItemInHand(payload.hand());
        ExportWandService.Result result =
                ExportWandService.INSTANCE.clearSelection(stack);
        ExportWandService.INSTANCE.playFeedback(player, result);
    }

    private static void handleAirEndpoint(
            SetWandAirEndpointPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        ItemStack stack = player.getItemInHand(payload.hand());
        if (!stack.is(MineToMeshContent.EXPORT_WAND_ITEM)) {
            return;
        }
        ExportWandService.Result result = ExportWandService.INSTANCE.setEndpoint(
                stack,
                player.level().dimension().identifier(),
                payload.endpoint(),
                WandAirTarget.twoBlocksAhead(
                        player.getEyePosition(), player.getLookAngle()),
                player.level().getMinY(),
                player.level().getMaxY() + 1);
        ExportWandService.INSTANCE.playFeedback(player, result);
    }

    private static void handleUpdateEndpoint(
            UpdateWandEndpointPayload payload, ServerPlayNetworking.Context context) {
        withBoundWand(context, (player, stack) ->
                ExportWandService.INSTANCE.setEndpoint(
                        stack,
                        player.level().dimension().identifier(),
                        payload.endpoint(),
                        payload.position(),
                        player.level().getMinY(),
                        player.level().getMaxY() + 1));
    }

    private static void handleToggleOverlay(
            ToggleWandOverlayPayload payload, ServerPlayNetworking.Context context) {
        withBoundWand(context, (player, stack) ->
                ExportWandService.INSTANCE.setOverlayEnabled(stack, payload.enabled()));
    }

    private static void handleToggleIncludePlayers(
            ToggleWandIncludePlayersPayload payload,
            ServerPlayNetworking.Context context) {
        withBoundWand(context, (player, stack) ->
                ExportWandService.INSTANCE.setIncludePlayers(stack, payload.enabled()));
    }

    private static void handleUpdateBatchSize(
            UpdateWandBatchSizePayload payload,
            ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (!(player.containerMenu instanceof ExportWandMenu menu)
                || !payload.wandId().equals(menu.binding().wandId())) {
            return;
        }
        menu.resolveBoundStack(player).ifPresent(stack -> {
            try {
                ExportWandService.INSTANCE.setBatchChunkCount(
                        stack, payload.batchChunkCount());
            } catch (IllegalArgumentException ignored) {
                // Invalid network values never mutate the bound wand.
            }
        });
    }

    private static void handleUpdateExportName(
            UpdateWandExportNamePayload payload, ServerPlayNetworking.Context context) {
        withBoundWand(context, (player, stack) -> {
            try {
                ExportName name = ExportName.parse(payload.exportName());
                ExportWandService.INSTANCE.setExportName(stack, name.value());
            } catch (IllegalArgumentException ignored) {
                // Invalid drafts remain client-local and never mutate the component.
            }
        });
    }

    private static void handleExportRequest(
            ExportWandRequestPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (!(player.containerMenu instanceof ExportWandMenu menu)) {
            return;
        }
        UUID wandId = menu.binding().wandId();
        ItemStack stack = menu.resolveBoundStack(player).orElse(null);
        if (stack == null) {
            sendReject(player, wandId, "minetomesh.error.wand.invalid_binding");
            return;
        }
        ExportWandSelection selection = stack.getOrDefault(
                MineToMeshContent.EXPORT_WAND_SELECTION,
                ExportWandSelection.empty());
        WandRequestPolicy.Validation validation =
                WandRequestPolicy.validateExportPermission(
                        context.server().isSingleplayer(),
                        player.createCommandSourceStack().permissions()
                                .hasPermission(Permissions.COMMANDS_GAMEMASTER));
        if (!validation.accepted()) {
            sendReject(player, wandId, validation.reasonKey());
            return;
        }
        validation = WandRequestPolicy.validateExportName(payload.exportName());
        if (!validation.accepted()) {
            sendReject(player, wandId, validation.reasonKey());
            return;
        }
        validation = WandRequestPolicy.validateSelection(
                selection, player.level().getMinY(), player.level().getMaxY() + 1);
        if (!validation.accepted()) {
            sendReject(player, wandId, validation.reasonKey());
            return;
        }
        validation = WandRequestPolicy.validateDimension(
                selection, player.level().dimension().identifier());
        if (!validation.accepted()) {
            sendReject(player, wandId, validation.reasonKey());
            return;
        }
        ExportWandService.INSTANCE.setExportName(
                stack, ExportName.parse(payload.exportName()).value());
        WandServerSessionReceiver.requestExport(
                new WandServerSessionReceiver.ExportRequest(
                        player, wandId, selection, payload.exportName()));
    }

    private static void sendReject(
            ServerPlayer player, UUID wandId, String reasonKey) {
        ServerPlayNetworking.send(
                player, new ExportWandRejectedPayload(wandId, reasonKey));
    }

    private static void withBoundWand(
            ServerPlayNetworking.Context context,
            BiConsumer<ServerPlayer, ItemStack> mutation) {
        ServerPlayer player = context.player();
        if (!(player.containerMenu instanceof ExportWandMenu menu)) {
            return;
        }
        menu.resolveBoundStack(player)
                .ifPresent(stack -> mutation.accept(player, stack));
    }
}
