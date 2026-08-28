package com.nebysse.minetomesh.content;

import com.nebysse.minetomesh.MineToMesh;
import com.nebysse.minetomesh.wand.ExportWandItem;
import com.nebysse.minetomesh.wand.ExportWandMenu;
import com.nebysse.minetomesh.wand.ExportWandSelection;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MineToMeshContent {
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MineToMesh.MOD_ID);
    private static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(
                    Registries.DATA_COMPONENT_TYPE, MineToMesh.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, MineToMesh.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MineToMesh.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ExportWandSelection>>
            EXPORT_WAND_SELECTION = DATA_COMPONENTS.registerComponentType(
                    "export_wand_selection",
                    builder -> builder.persistent(ExportWandSelection.CODEC)
                            .networkSynchronized(ExportWandSelection.STREAM_CODEC));

    public static final DeferredItem<ExportWandItem> EXPORT_WAND_ITEM =
            ITEMS.register("export_wand",
                    () -> new ExportWandItem(new Item.Properties().stacksTo(1)
                            .setId(ResourceKey.create(Registries.ITEM,
                                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                            MineToMesh.MOD_ID, "export_wand")))));

    public static final DeferredHolder<MenuType<?>, MenuType<ExportWandMenu>>
            EXPORT_WAND_MENU = MENUS.register(
                    "export_wand",
                    () -> IMenuTypeExtension.create(ExportWandMenu.FACTORY));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB =
            CREATIVE_TABS.register("mine_to_mesh",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.minetomesh"))
                            .icon(() -> new ItemStack(EXPORT_WAND_ITEM.get()))
                            .displayItems((parameters, output) ->
                                    output.accept(EXPORT_WAND_ITEM.get()))
                            .build());

    private MineToMeshContent() {
    }

    public static void register(IEventBus modBus) {
        DATA_COMPONENTS.register(modBus);
        ITEMS.register(modBus);
        MENUS.register(modBus);
        CREATIVE_TABS.register(modBus);
    }
}
