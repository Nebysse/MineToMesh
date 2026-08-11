package com.nebysse.minetomesh.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

class ExportWandContentTest {
    @Test
    void registersTypedWandContent() {
        assertEquals("export_wand",
                MineToMeshContent.EXPORT_WAND_ITEM.getKey().location().getPath());
        assertEquals(1,
                new ItemStack(MineToMeshContent.EXPORT_WAND_ITEM.get()).getMaxStackSize());
        assertEquals("export_wand_selection",
                MineToMeshContent.EXPORT_WAND_SELECTION.getKey().location().getPath());
        assertEquals("export_wand",
                MineToMeshContent.EXPORT_WAND_MENU.getKey().location().getPath());
        assertNotNull(MineToMeshContent.EXPORT_WAND_SELECTION.get());
        assertNotNull(MineToMeshContent.EXPORT_WAND_MENU.get());
    }

    @Test
    void creativeTabRegistrationRemainsMineToMeshOwned() {
        assertEquals("mine_to_mesh",
                MineToMeshContent.CREATIVE_TAB.getKey().location().getPath());
        assertTrue(MineToMeshContent.EXPORT_WAND_ITEM.get().getDefaultInstance()
                .is(MineToMeshContent.EXPORT_WAND_ITEM.get()));
    }
}
