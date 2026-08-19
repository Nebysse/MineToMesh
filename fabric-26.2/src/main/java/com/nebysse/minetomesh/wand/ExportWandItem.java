package com.nebysse.minetomesh.wand;

import java.util.UUID;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
            return sidedSuccess(context.getLevel());
        }
        if (!context.getLevel().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ExportWandService.Result result = ExportWandService.INSTANCE.setEndpoint(
                    context.getItemInHand(),
                    context.getLevel().dimension().identifier(),
                    Endpoint.POS2,
                    context.getClickedPos(),
                    context.getLevel().getMinY(),
                    context.getLevel().getMaxY() + 1);
            ExportWandService.INSTANCE.playFeedback(serverPlayer, result);
        }
        return sidedSuccess(context.getLevel());
    }

    @Override
    public InteractionResult use(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            openMenu(player, hand);
            return sidedSuccess(level);
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ExportWandService.Result result = ExportWandService.INSTANCE.setEndpoint(
                    stack,
                    level.dimension().identifier(),
                    Endpoint.POS2,
                    WandAirTarget.twoBlocksAhead(
                            player.getEyePosition(), player.getLookAngle()),
                    level.getMinY(),
                    level.getMaxY() + 1);
            ExportWandService.INSTANCE.playFeedback(serverPlayer, result);
        }
        return sidedSuccess(level);
    }

    private static InteractionResult sidedSuccess(Level level) {
        return level.isClientSide()
                ? InteractionResult.SUCCESS
                : InteractionResult.SUCCESS_SERVER;
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
                hand, player.getInventory().getSelectedSlot());
        WandBinding binding = new WandBinding(hand, slot, wandId);
        serverPlayer.openMenu(new ExtendedMenuProvider<WandBinding>() {
            @Override
            public WandBinding getScreenOpeningData(ServerPlayer ignored) {
                return binding;
            }

            @Override
            public AbstractContainerMenu createMenu(
                    int containerId, Inventory inventory, Player ignored) {
                return new ExportWandMenu(containerId, inventory, binding);
            }

            @Override
            public Component getDisplayName() {
                return Component.translatable("item.minetomesh.export_wand");
            }
        });
        ExportWandService.INSTANCE.playFeedback(
                serverPlayer, ExportWandService.Result.UPDATED);
    }
}
