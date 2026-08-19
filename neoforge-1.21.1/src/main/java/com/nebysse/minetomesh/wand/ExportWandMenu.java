package com.nebysse.minetomesh.wand;

import com.nebysse.minetomesh.content.MineToMeshContent;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.IContainerFactory;

public final class ExportWandMenu extends AbstractContainerMenu {
    public static final IContainerFactory<ExportWandMenu> FACTORY =
            ExportWandMenu::new;

    private final Inventory inventory;
    private final WandBinding binding;

    public ExportWandMenu(
            int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, WandBinding.decode(buffer));
    }

    public ExportWandMenu(
            int containerId, Inventory inventory, WandBinding binding) {
        super(MineToMeshContent.EXPORT_WAND_MENU.get(), containerId);
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
                MineToMeshContent.EXPORT_WAND_SELECTION.get(),
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
