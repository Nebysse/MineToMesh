package com.onecuber.mcgltf.testmod;

import com.onecuber.mcgltf.content.McGltfContent;
import com.onecuber.mcgltf.wand.Endpoint;
import com.onecuber.mcgltf.wand.ExportWandSelection;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

public final class WandGameTests {
    private WandGameTests() {
    }

    @GameTest(template = "empty")
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
        ItemStack original = new ItemStack(McGltfContent.EXPORT_WAND_ITEM.get());
        original.set(McGltfContent.EXPORT_WAND_SELECTION.get(), expected);
        ExportWandSelection actual = original.copy().get(
                McGltfContent.EXPORT_WAND_SELECTION.get());
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
