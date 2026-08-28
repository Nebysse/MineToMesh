package com.nebysse.minetomesh.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nebysse.minetomesh.content.MineToMeshContent;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class WandBindingTest {
    private static final UUID WAND_ID =
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @Test
    void bindingMatchesOnlyTheSameTypedWandIdentity() {
        ItemStack wand = new ItemStack(MineToMeshContent.EXPORT_WAND_ITEM.get());
        wand.set(MineToMeshContent.EXPORT_WAND_SELECTION.get(),
                ExportWandSelection.empty().ensureIdentity(WAND_ID));
        WandBinding binding = new WandBinding(
                InteractionHand.MAIN_HAND, 3, WAND_ID);
        assertTrue(binding.matches(wand));
        assertFalse(new WandBinding(InteractionHand.MAIN_HAND, 3,
                UUID.fromString("223e4567-e89b-12d3-a456-426614174000"))
                .matches(wand));
        assertFalse(binding.matches(new ItemStack(Items.STICK)));
    }

    @Test
    void bindingRoundTripsMenuOpenData() {
        WandBinding original = new WandBinding(
                InteractionHand.OFF_HAND, WandBinding.OFFHAND_SLOT, WAND_ID);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        original.encode(buffer);
        buffer.readerIndex(0);
        assertEquals(original, WandBinding.decode(buffer));
    }

    @Test
    void handMapsToStableInventorySlot() {
        assertEquals(7, WandBinding.inventorySlot(
                InteractionHand.MAIN_HAND, 7));
        assertEquals(WandBinding.OFFHAND_SLOT, WandBinding.inventorySlot(
                InteractionHand.OFF_HAND, 7));
    }
}
