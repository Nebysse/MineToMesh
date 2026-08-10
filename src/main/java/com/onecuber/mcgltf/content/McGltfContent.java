package com.onecuber.mcgltf.content;

import com.onecuber.mcgltf.McGltf;
import com.onecuber.mcgltf.workstation.ExportWorkstationBlock;
import com.onecuber.mcgltf.workstation.ExportWorkstationBlockEntity;
import com.onecuber.mcgltf.workstation.ExportWorkstationMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class McGltfContent {
    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(McGltf.MOD_ID);
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(McGltf.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, McGltf.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, McGltf.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, McGltf.MOD_ID);

    public static final DeferredBlock<ExportWorkstationBlock> EXPORT_WORKSTATION_BLOCK =
            BLOCKS.register("export_workstation", ExportWorkstationBlock::new);

    public static final DeferredItem<BlockItem> EXPORT_WORKSTATION_ITEM =
            ITEMS.register("export_workstation",
                    () -> new BlockItem(EXPORT_WORKSTATION_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ExportWorkstationBlockEntity>>
            EXPORT_WORKSTATION_BLOCK_ENTITY = BLOCK_ENTITIES.register(
                    "export_workstation",
                    () -> BlockEntityType.Builder.of(
                            ExportWorkstationBlockEntity::new,
                            EXPORT_WORKSTATION_BLOCK.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<ExportWorkstationMenu>>
            EXPORT_WORKSTATION_MENU = MENUS.register(
                    "export_workstation",
                    () -> IMenuTypeExtension.create(ExportWorkstationMenu.FACTORY));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB =
            CREATIVE_TABS.register("mine_to_mesh",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.mcgltf"))
                            .icon(() -> new ItemStack(EXPORT_WORKSTATION_BLOCK.asItem()))
                            .displayItems((parameters, output) ->
                                    output.accept(EXPORT_WORKSTATION_BLOCK.asItem()))
                            .build());

    private McGltfContent() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        CREATIVE_TABS.register(modBus);
    }
}
