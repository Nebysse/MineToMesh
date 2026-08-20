package com.nebysse.minetomesh.network;

import com.nebysse.minetomesh.content.MineToMeshContent;
import com.nebysse.minetomesh.output.ExportName;
import com.nebysse.minetomesh.wand.ExportWandMenu;
import com.nebysse.minetomesh.wand.ExportWandSelection;
import com.nebysse.minetomesh.wand.WandAirTarget;
import com.nebysse.minetomesh.wand.ExportWandService;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class WandPayloads {
    private WandPayloads() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(WandPayloads::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ClearWandSelectionPayload.TYPE,
                ClearWandSelectionPayload.STREAM_CODEC,
                WandPayloads::handleClearSelection);
        registrar.playToServer(SetWandAirEndpointPayload.TYPE,
                SetWandAirEndpointPayload.STREAM_CODEC,
                WandPayloads::handleAirEndpoint);
        registrar.playToServer(UpdateWandEndpointPayload.TYPE,
                UpdateWandEndpointPayload.STREAM_CODEC,
                WandPayloads::handleUpdateEndpoint);
        registrar.playToServer(ToggleWandOverlayPayload.TYPE,
                ToggleWandOverlayPayload.STREAM_CODEC,
                WandPayloads::handleToggleOverlay);
        registrar.playToServer(ToggleWandIncludePlayersPayload.TYPE,
                ToggleWandIncludePlayersPayload.STREAM_CODEC,
                WandPayloads::handleToggleIncludePlayers);
        registrar.playToServer(UpdateWandBatchSizePayload.TYPE,
                UpdateWandBatchSizePayload.STREAM_CODEC,
                WandPayloads::handleUpdateBatchSize);
        registrar.playToServer(UpdateWandExportNamePayload.TYPE,
                UpdateWandExportNamePayload.STREAM_CODEC,
                WandPayloads::handleUpdateExportName);
        registrar.playToServer(ExportWandRequestPayload.TYPE,
                ExportWandRequestPayload.STREAM_CODEC,
                WandPayloads::handleExportRequest);
        registrar.playToClient(ExportWandGrantedPayload.TYPE,
                ExportWandGrantedPayload.STREAM_CODEC,
                (payload, context) -> WandClientReceiver.receive(payload));
        registrar.playToClient(ExportWandRejectedPayload.TYPE,
                ExportWandRejectedPayload.STREAM_CODEC,
                (payload, context) -> WandClientReceiver.receive(payload));
    }

    private static void handleClearSelection(
            ClearWandSelectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack stack = player.getItemInHand(payload.hand());
            ExportWandService.Result result =
                    ExportWandService.INSTANCE.clearSelection(stack);
            ExportWandService.INSTANCE.playFeedback(player, result);
        });
    }

    private static void handleAirEndpoint(
            SetWandAirEndpointPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack stack = player.getItemInHand(payload.hand());
            if (!stack.is(MineToMeshContent.EXPORT_WAND_ITEM.get())) {
                return;
            }
            ExportWandService.Result result =
                    ExportWandService.INSTANCE.setEndpoint(
                            stack,
                            player.level().dimension().location(),
                            payload.endpoint(),
                            WandAirTarget.twoBlocksAhead(
                                    player.getEyePosition(), player.getLookAngle()),
                            player.level().getMinBuildHeight(),
                            player.level().getMaxBuildHeight());
            ExportWandService.INSTANCE.playFeedback(player, result);
        });
    }

    private static void handleUpdateEndpoint(
            UpdateWandEndpointPayload payload, IPayloadContext context) {
        withBoundWand(context, (player, stack) ->
                ExportWandService.INSTANCE.setEndpoint(
                        stack,
                        player.level().dimension().location(),
                        payload.endpoint(),
                        payload.position(),
                        player.level().getMinBuildHeight(),
                        player.level().getMaxBuildHeight()));
    }

    private static void handleToggleOverlay(
            ToggleWandOverlayPayload payload, IPayloadContext context) {
        withBoundWand(context, (player, stack) ->
                ExportWandService.INSTANCE.setOverlayEnabled(
                        stack, payload.enabled()));
    }

    private static void handleToggleIncludePlayers(
            ToggleWandIncludePlayersPayload payload, IPayloadContext context) {
        withBoundWand(context, (player, stack) ->
                ExportWandService.INSTANCE.setIncludePlayers(stack, payload.enabled()));
    }

    private static void handleUpdateBatchSize(
            UpdateWandBatchSizePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof ExportWandMenu menu)
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
        });
    }

    private static void handleUpdateExportName(
            UpdateWandExportNamePayload payload, IPayloadContext context) {
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
            ExportWandRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof ExportWandMenu menu)) {
                return;
            }
            UUID wandId = menu.binding().wandId();
            ItemStack stack = menu.resolveBoundStack(player).orElse(null);
            if (stack == null) {
                sendReject(player, wandId, "minetomesh.error.wand.invalid_binding");
                return;
            }
            ExportWandSelection selection = stack.getOrDefault(
                    MineToMeshContent.EXPORT_WAND_SELECTION.get(),
                    ExportWandSelection.empty());
            WandRequestPolicy.Validation validation =
                    WandRequestPolicy.validateExportPermission(
                            player.getServer().isSingleplayer(),
                            player.createCommandSourceStack().hasPermission(2));
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
                    selection,
                    player.level().getMinBuildHeight(),
                    player.level().getMaxBuildHeight());
            if (!validation.accepted()) {
                sendReject(player, wandId, validation.reasonKey());
                return;
            }
            validation = WandRequestPolicy.validateDimension(
                    selection, player.level().dimension().location());
            if (!validation.accepted()) {
                sendReject(player, wandId, validation.reasonKey());
                return;
            }
            ExportWandService.INSTANCE.setExportName(
                    stack, ExportName.parse(payload.exportName()).value());
            PacketDistributor.sendToPlayer(player, new ExportWandGrantedPayload(
                    wandId,
                    payload.exportName(),
                    selection.pos1().orElseThrow(),
                    selection.pos2().orElseThrow(),
                    selection.selectionDimension().orElseThrow().toString(),
                    selection.includePlayers()));
        });
    }

    private static void sendReject(
            ServerPlayer player, UUID wandId, String reasonKey) {
        PacketDistributor.sendToPlayer(
                player, new ExportWandRejectedPayload(wandId, reasonKey));
    }

    private static void withBoundWand(
            IPayloadContext context, BiConsumer<ServerPlayer, ItemStack> mutation) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof ExportWandMenu menu)) {
                return;
            }
            menu.resolveBoundStack(player)
                    .ifPresent(stack -> mutation.accept(player, stack));
        });
    }
}
