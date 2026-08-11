package com.onecuber.mcgltf.network;

import com.onecuber.mcgltf.wand.ExportWandService;
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
}
