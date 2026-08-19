package com.nebysse.minetomesh.wand;

import com.nebysse.minetomesh.content.MineToMeshContent;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public record WandBinding(InteractionHand hand, int inventorySlot, UUID wandId) {
    public static final int OFFHAND_SLOT = 40;
    public static final StreamCodec<FriendlyByteBuf, WandBinding> STREAM_CODEC =
            StreamCodec.of((buffer, value) -> value.encode(buffer), WandBinding::decode);

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
        return matches(stack) ? Optional.of(stack) : Optional.empty();
    }

    public boolean matches(ItemStack stack) {
        if (stack == null || !stack.is(MineToMeshContent.EXPORT_WAND_ITEM)) {
            return false;
        }
        ExportWandSelection selection = stack.get(
                MineToMeshContent.EXPORT_WAND_SELECTION);
        return selection != null && selection.wandId().isPresent()
                && wandId.equals(selection.wandId().orElseThrow());
    }

    public static int inventorySlot(InteractionHand hand, int selectedSlot) {
        Objects.requireNonNull(hand, "hand");
        return hand == InteractionHand.MAIN_HAND ? selectedSlot : OFFHAND_SLOT;
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
