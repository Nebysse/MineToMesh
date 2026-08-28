package com.nebysse.minetomesh.testmod;

import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class TestContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(
            BuiltInRegistries.BLOCK, MineToMeshTestMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(
            BuiltInRegistries.ITEM, MineToMeshTestMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, MineToMeshTestMod.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(
            BuiltInRegistries.ENTITY_TYPE, MineToMeshTestMod.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(
            BuiltInRegistries.FLUID, MineToMeshTestMod.MOD_ID);
    private static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(
            NeoForgeRegistries.Keys.FLUID_TYPES, MineToMeshTestMod.MOD_ID);

    public static final DeferredHolder<FluidType, FluidType> TEST_FLUID_TYPE = FLUID_TYPES.register(
            "test_fluid", () -> new FluidType(FluidType.Properties.create()));

    private static final BaseFlowingFluid.Properties FLUID_PROPERTIES =
            new BaseFlowingFluid.Properties(
                    TestContent.TEST_FLUID_TYPE::get,
                    () -> TestContent.TEST_FLUID.get(),
                    () -> TestContent.FLOWING_TEST_FLUID.get())
                    .block(() -> TestContent.TEST_FLUID_BLOCK.get())
                    .bucket(() -> TestContent.TEST_FLUID_BUCKET.get());

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> TEST_FLUID = FLUIDS.register(
            "test_fluid", () -> new BaseFlowingFluid.Source(FLUID_PROPERTIES));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_TEST_FLUID = FLUIDS.register(
            "flowing_test_fluid", () -> new BaseFlowingFluid.Flowing(FLUID_PROPERTIES));

    public static final DeferredHolder<Block, FixtureBlock> MODEL_DATA_BLOCK = BLOCKS.register(
            "model_data_block",
            () -> new FixtureBlock(
                    BlockBehaviour.Properties.of().strength(1.0F)
                            .setId(blockKey("model_data_block")),
                    RenderShape.MODEL,
                    TestModelDataBlockEntity::new));
    public static final DeferredHolder<Block, FixtureBlock> RENDERED_BLOCK = BLOCKS.register(
            "rendered_block",
            () -> new FixtureBlock(
                    BlockBehaviour.Properties.of().strength(1.0F)
                            .setId(blockKey("rendered_block")),
                    RenderShape.INVISIBLE,
                    TestRenderedBlockEntity::new));
    public static final DeferredHolder<Block, FixtureBlock> GPU_ONLY_BLOCK = BLOCKS.register(
            "gpu_only_block",
            () -> new FixtureBlock(
                    BlockBehaviour.Properties.of().strength(1.0F)
                            .setId(blockKey("gpu_only_block")),
                    RenderShape.INVISIBLE,
                    GpuOnlyBlockEntity::new));
    public static final DeferredHolder<Block, LiquidBlock> TEST_FLUID_BLOCK = BLOCKS.register(
            "test_fluid_block",
            () -> new LiquidBlock(TestContent.TEST_FLUID.get(),
                    BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.WATER)
                            .setId(blockKey("test_fluid_block"))));

    public static final DeferredHolder<Item, BlockItem> MODEL_DATA_BLOCK_ITEM = ITEMS.register(
            "model_data_block", () -> new BlockItem(MODEL_DATA_BLOCK.get(),
                    new Item.Properties().setId(itemKey("model_data_block"))));
    public static final DeferredHolder<Item, BlockItem> RENDERED_BLOCK_ITEM = ITEMS.register(
            "rendered_block", () -> new BlockItem(RENDERED_BLOCK.get(),
                    new Item.Properties().setId(itemKey("rendered_block"))));
    public static final DeferredHolder<Item, BlockItem> GPU_ONLY_BLOCK_ITEM = ITEMS.register(
            "gpu_only_block", () -> new BlockItem(GPU_ONLY_BLOCK.get(),
                    new Item.Properties().setId(itemKey("gpu_only_block"))));
    public static final DeferredHolder<Item, BucketItem> TEST_FLUID_BUCKET = ITEMS.register(
            "test_fluid_bucket",
            () -> new BucketItem(TEST_FLUID.get(),
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)
                            .setId(itemKey("test_fluid_bucket"))));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TestModelDataBlockEntity>>
            MODEL_DATA_BLOCK_ENTITY = BLOCK_ENTITIES.register(
                    "model_data_block",
                    () -> new BlockEntityType<>(
                            TestModelDataBlockEntity::new, MODEL_DATA_BLOCK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TestRenderedBlockEntity>>
            RENDERED_BLOCK_ENTITY = BLOCK_ENTITIES.register(
                    "rendered_block",
                    () -> new BlockEntityType<>(
                            TestRenderedBlockEntity::new, RENDERED_BLOCK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GpuOnlyBlockEntity>>
            GPU_ONLY_BLOCK_ENTITY = BLOCK_ENTITIES.register(
                    "gpu_only_block",
                    () -> new BlockEntityType<>(
                            GpuOnlyBlockEntity::new, GPU_ONLY_BLOCK.get()));

    public static final DeferredHolder<EntityType<?>, EntityType<TestEntity>> TEST_ENTITY = ENTITIES.register(
            "test_entity",
            () -> EntityType.Builder.<TestEntity>of(TestEntity::new, MobCategory.MISC)
                    .sized(0.8F, 1.2F)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,
                            ResourceLocation.fromNamespaceAndPath(
                                    MineToMeshTestMod.MOD_ID, "test_entity"))));

    private static ResourceKey<Block> blockKey(String name) {
        return ResourceKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(MineToMeshTestMod.MOD_ID, name));
    }

    private static ResourceKey<Item> itemKey(String name) {
        return ResourceKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(MineToMeshTestMod.MOD_ID, name));
    }

    private TestContent() {
    }

    public static void register(IEventBus modBus) {
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
    }

    public static final class FixtureBlock extends Block implements EntityBlock {
        private final RenderShape renderShape;
        private final BiFunction<BlockPos, BlockState, BlockEntity> factory;

        private FixtureBlock(
                Properties properties,
                RenderShape renderShape,
                BiFunction<BlockPos, BlockState, BlockEntity> factory) {
            super(properties);
            this.renderShape = renderShape;
            this.factory = factory;
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return renderShape;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
            return factory.apply(position, state);
        }
    }
}
