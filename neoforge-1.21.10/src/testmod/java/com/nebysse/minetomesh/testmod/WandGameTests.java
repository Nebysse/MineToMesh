package com.nebysse.minetomesh.testmod;

import com.nebysse.minetomesh.content.MineToMeshContent;
import com.nebysse.minetomesh.wand.Endpoint;
import com.nebysse.minetomesh.wand.ExportWandSelection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WandGameTests {
    private static final ResourceLocation SELECTION_ROUND_TRIP =
            ResourceLocation.fromNamespaceAndPath(
                    MineToMeshTestMod.MOD_ID, "wand_selection_round_trip");

    // TEST_FUNCTION 注册表只在 RegisterEvent 派发窗口解冻，因此走 DeferredRegister。
    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
            DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, MineToMeshTestMod.MOD_ID);
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
            SELECTION_ROUND_TRIP_FUNCTION = TEST_FUNCTIONS.register(
                    "wand_selection_round_trip",
                    () -> (Consumer<GameTestHelper>)
                            WandGameTests::itemStackCopyRoundTripsSelectionComponent);

    private WandGameTests() {
    }

    // 1.21.10 起 @GameTest 注解被移除，测试实例改为注册表驱动。
    public static void registerGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition> environment = event.registerEnvironment(
                ResourceLocation.fromNamespaceAndPath(MineToMeshTestMod.MOD_ID, "default"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(SELECTION_ROUND_TRIP, new FunctionGameTestInstance(
                ResourceKey.create(Registries.TEST_FUNCTION, SELECTION_ROUND_TRIP),
                new TestData<>(environment,
                        ResourceLocation.withDefaultNamespace("empty"),
                        100, 0, true)));
    }

    public static void itemStackCopyRoundTripsSelectionComponent(
            GameTestHelper helper) {
        ExportWandSelection expected = ExportWandSelection.empty()
                .ensureIdentity(UUID.fromString(
                        "123e4567-e89b-12d3-a456-426614174000"))
                .setEndpoint(ResourceLocation.parse("minecraft:overworld"),
                        Endpoint.POS1, new BlockPos(-3, 64, 9))
                .setEndpoint(ResourceLocation.parse("minecraft:overworld"),
                        Endpoint.POS2, new BlockPos(12, 80, 25))
                .withExportName("gametest_export");
        ItemStack original = new ItemStack(MineToMeshContent.EXPORT_WAND_ITEM.get());
        original.set(MineToMeshContent.EXPORT_WAND_SELECTION.get(), expected);
        ExportWandSelection actual = original.copy().get(
                MineToMeshContent.EXPORT_WAND_SELECTION.get());
        if (!expected.equals(actual)) {
            helper.fail("wand selection component did not survive ItemStack copy");
            return;
        }
        helper.succeed();
    }

    public static void onServerStarted(ServerStartedEvent event) {
        System.out.println("MINETOMESH_SERVER_READY");
        event.getServer().execute(() -> event.getServer().halt(false));
    }
}
