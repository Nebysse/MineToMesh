package com.nebysse.minetomesh.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Requires Fabric game bootstrap; covered by dedicated-server smoke")
class ExportWandContentTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        MineToMeshContent.register();
    }

    @Test
    void declaresTypedWandContentWithStableIds() {
        assertEquals("export_wand", MineToMeshContent.EXPORT_WAND_ID.getPath());
        assertEquals(1,
                new ItemStack(MineToMeshContent.EXPORT_WAND_ITEM).getMaxStackSize());
        assertEquals("export_wand_selection",
                MineToMeshContent.EXPORT_WAND_SELECTION_ID.getPath());
        assertEquals("export_wand", MineToMeshContent.EXPORT_WAND_MENU_ID.getPath());
        assertNotNull(MineToMeshContent.EXPORT_WAND_SELECTION);
        assertNotNull(MineToMeshContent.EXPORT_WAND_MENU);
    }

    @Test
    void creativeTabAndWandRemainMineToMeshOwned() {
        assertEquals("mine_to_mesh", MineToMeshContent.CREATIVE_TAB_ID.getPath());
        assertTrue(MineToMeshContent.EXPORT_WAND_ITEM.getDefaultInstance()
                .is(MineToMeshContent.EXPORT_WAND_ITEM));
    }
}
