package com.nebysse.minetomesh.wand;

import com.nebysse.minetomesh.content.MineToMeshContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class WandInteractionHandler {
    private WandInteractionHandler() {
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        InteractionHand hand = resolveWandHand(player);
        if (hand == null) {
            return;
        }
        event.setCanceled(true);
        if (!event.getLevel().isClientSide()
                && event.getAction()
                == PlayerInteractEvent.LeftClickBlock.Action.START
                && player instanceof ServerPlayer serverPlayer) {
            ExportWandService.Result result = ExportWandService.INSTANCE.setEndpoint(
                    player.getItemInHand(hand),
                    event.getLevel().dimension().location(),
                    Endpoint.POS1,
                    event.getPos(),
                    event.getLevel().getMinBuildHeight(),
                    event.getLevel().getMaxBuildHeight());
            ExportWandService.INSTANCE.playFeedback(serverPlayer, result);
        }
    }

    public static InteractionHand resolveWandHand(Player player) {
        if (player.getMainHandItem().is(MineToMeshContent.EXPORT_WAND_ITEM.get())) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().is(MineToMeshContent.EXPORT_WAND_ITEM.get())) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }
}
