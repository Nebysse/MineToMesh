package com.nebysse.minetomesh.content;

import com.nebysse.minetomesh.MineToMeshInfo;
import com.nebysse.minetomesh.wand.ExportWandItem;
import com.nebysse.minetomesh.wand.ExportWandMenu;
import com.nebysse.minetomesh.wand.ExportWandSelection;
import com.nebysse.minetomesh.wand.WandBinding;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class MineToMeshContent {
    public static final Identifier EXPORT_WAND_ID = id("export_wand");
    public static final Identifier EXPORT_WAND_SELECTION_ID = id("export_wand_selection");
    public static final Identifier EXPORT_WAND_MENU_ID = id("export_wand");
    public static final Identifier CREATIVE_TAB_ID = id("mine_to_mesh");

    private static final ResourceKey<Item> EXPORT_WAND_KEY =
            ResourceKey.create(Registries.ITEM, EXPORT_WAND_ID);

    public static final DataComponentType<ExportWandSelection> EXPORT_WAND_SELECTION =
            DataComponentType.<ExportWandSelection>builder()
                    .persistent(ExportWandSelection.CODEC)
                    .networkSynchronized(ExportWandSelection.STREAM_CODEC)
                    .build();

    public static final ExportWandItem EXPORT_WAND_ITEM =
            new ExportWandItem(new Item.Properties().stacksTo(1).setId(EXPORT_WAND_KEY));

    public static final MenuType<ExportWandMenu> EXPORT_WAND_MENU =
            new ExtendedMenuType<ExportWandMenu, WandBinding>(
                    ExportWandMenu::new, WandBinding.STREAM_CODEC);

    public static final CreativeModeTab CREATIVE_TAB =
            FabricCreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.minetomesh"))
                    .icon(() -> new ItemStack(EXPORT_WAND_ITEM))
                    .displayItems((parameters, output) -> output.accept(EXPORT_WAND_ITEM))
                    .build();

    private static boolean registered;

    private MineToMeshContent() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                EXPORT_WAND_SELECTION_ID, EXPORT_WAND_SELECTION);
        Registry.register(BuiltInRegistries.ITEM, EXPORT_WAND_KEY, EXPORT_WAND_ITEM);
        Registry.register(BuiltInRegistries.MENU, EXPORT_WAND_MENU_ID, EXPORT_WAND_MENU);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CREATIVE_TAB_ID, CREATIVE_TAB);
        registered = true;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MineToMeshInfo.MOD_ID, path);
    }
}
