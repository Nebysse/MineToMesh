package com.onecuber.mcgltf.network;

import com.onecuber.mcgltf.output.ExportName;
import com.onecuber.mcgltf.wand.ExportWandMenu;
import com.onecuber.mcgltf.wand.ExportWandService;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
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
        registrar.playToServer(UpdateWandEndpointPayload.TYPE,
                UpdateWandEndpointPayload.STREAM_CODEC,
                WandPayloads::handleUpdateEndpoint);
        registrar.playToServer(ToggleWandOverlayPayload.TYPE,
                ToggleWandOverlayPayload.STREAM_CODEC,
                WandPayloads::handleToggleOverlay);
        registrar.playToServer(UpdateWandExportNamePayload.TYPE,
                UpdateWandExportNamePayload.STREAM_CODEC,
                WandPayloads::handleUpdateExportName);
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
