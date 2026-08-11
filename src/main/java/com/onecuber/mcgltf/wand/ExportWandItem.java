package com.onecuber.mcgltf.wand;

import com.onecuber.mcgltf.content.McGltfContent;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class ExportWandItem extends Item {
    public ExportWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            openMenu(player, context.getHand());
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
        }
        if (!context.getLevel().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ExportWandService.Result result = ExportWandService.INSTANCE.setEndpoint(
                    context.getItemInHand(),
                    context.getLevel().dimension().location(),
                    Endpoint.POS2,
                    context.getClickedPos(),
                    context.getLevel().getMinBuildHeight(),
                    context.getLevel().getMaxBuildHeight());
            ExportWandService.INSTANCE.playFeedback(serverPlayer, result);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive()) {
            return InteractionResultHolder.pass(stack);
        }
        openMenu(player, hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static void openMenu(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        ExportWandService.INSTANCE.ensureIdentity(stack);
        ExportWandSelection selection = ExportWandService.INSTANCE.selection(stack);
        UUID wandId = selection.wandId().orElseThrow();
        int slot = WandBinding.inventorySlot(
                hand, player.getInventory().selected);
        WandBinding binding = new WandBinding(hand, slot, wandId);
        serverPlayer.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, ignored) ->
                                new ExportWandMenu(containerId, inventory, binding),
                        Component.translatable("item.mcgltf.export_wand")),
                binding::encode);
        ExportWandService.INSTANCE.playFeedback(
                serverPlayer, ExportWandService.Result.UPDATED);
    }
}
