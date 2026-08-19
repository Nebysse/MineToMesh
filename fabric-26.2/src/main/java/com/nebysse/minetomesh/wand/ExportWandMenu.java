package com.nebysse.minetomesh.wand;

import com.nebysse.minetomesh.content.MineToMeshContent;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class ExportWandMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final WandBinding binding;

    public ExportWandMenu(
            int containerId, Inventory inventory, WandBinding binding) {
        super(MineToMeshContent.EXPORT_WAND_MENU, containerId);
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.binding = Objects.requireNonNull(binding, "binding");
    }

    public WandBinding binding() {
        return binding;
    }

    public Optional<ItemStack> resolveBoundStack(Player player) {
        return binding.resolve(player);
    }

    public ExportWandSelection selection() {
        ItemStack stack = inventory.getItem(binding.inventorySlot());
        return stack.getOrDefault(
                MineToMeshContent.EXPORT_WAND_SELECTION,
                ExportWandSelection.empty());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return binding.resolve(player).isPresent();
    }
}
