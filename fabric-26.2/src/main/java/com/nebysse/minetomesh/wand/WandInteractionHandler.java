package com.nebysse.minetomesh.wand;

import com.nebysse.minetomesh.content.MineToMeshContent;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class WandInteractionHandler {
    private WandInteractionHandler() {
    }

    public static void register() {
        AttackBlockCallback.EVENT.register(WandInteractionHandler::onAttackBlock);
    }

    private static InteractionResult onAttackBlock(
            Player player,
            Level level,
            InteractionHand ignoredHand,
            BlockPos position,
            Direction ignoredDirection) {
        InteractionHand wandHand = resolveWandHand(player);
        if (wandHand == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ExportWandService.Result result = ExportWandService.INSTANCE.setEndpoint(
                    player.getItemInHand(wandHand),
                    level.dimension().identifier(),
                    Endpoint.POS1,
                    position,
                    level.getMinY(),
                    level.getMaxY() + 1);
            ExportWandService.INSTANCE.playFeedback(serverPlayer, result);
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.SUCCESS;
    }

    public static InteractionHand resolveWandHand(Player player) {
        if (player.getMainHandItem().is(MineToMeshContent.EXPORT_WAND_ITEM)) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().is(MineToMeshContent.EXPORT_WAND_ITEM)) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }
}
