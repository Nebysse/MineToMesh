package com.onecuber.mcgltf.wand;

import com.onecuber.mcgltf.content.McGltfContent;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public record WandBinding(InteractionHand hand, int inventorySlot, UUID wandId) {
    public static final int OFFHAND_SLOT = 40;

    public WandBinding {
        Objects.requireNonNull(hand, "hand");
        Objects.requireNonNull(wandId, "wandId");
        if (inventorySlot < 0) {
            throw new IllegalArgumentException("inventorySlot must not be negative");
        }
    }

    public Optional<ItemStack> resolve(Player player) {
        Objects.requireNonNull(player, "player");
        if (inventorySlot >= player.getInventory().getContainerSize()) {
            return Optional.empty();
        }
        ItemStack stack = player.getInventory().getItem(inventorySlot);
        if (!stack.is(McGltfContent.EXPORT_WAND_ITEM.get())) {
            return Optional.empty();
        }
        ExportWandSelection selection = stack.get(
                McGltfContent.EXPORT_WAND_SELECTION.get());
        if (selection == null || selection.wandId().isEmpty()
                || !wandId.equals(selection.wandId().orElseThrow())) {
            return Optional.empty();
        }
        return Optional.of(stack);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(hand);
        buffer.writeVarInt(inventorySlot);
        buffer.writeUUID(wandId);
    }

    public static WandBinding decode(FriendlyByteBuf buffer) {
        return new WandBinding(
                buffer.readEnum(InteractionHand.class),
                buffer.readVarInt(),
                buffer.readUUID());
    }
}
