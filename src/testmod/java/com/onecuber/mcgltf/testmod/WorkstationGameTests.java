package com.onecuber.mcgltf.testmod;

import com.onecuber.mcgltf.content.McGltfContent;
import com.onecuber.mcgltf.workstation.ExportWorkstationBlock;
import com.onecuber.mcgltf.workstation.ExportWorkstationBlockEntity;
import com.onecuber.mcgltf.workstation.WorkstationCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

public final class WorkstationGameTests {
    private WorkstationGameTests() {
    }

    @GameTest(template = "empty")
    public static void placeEachFacing(GameTestHelper helper) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos pos = new BlockPos(facing.get2DDataValue() * 3, 1, 0);
            helper.setBlock(pos, McGltfContent.EXPORT_WORKSTATION_BLOCK.get()
                    .defaultBlockState().setValue(ExportWorkstationBlock.FACING, facing));
            BlockState state = helper.getBlockState(pos);
            if (state.getValue(ExportWorkstationBlock.FACING) != facing) {
                helper.fail("facing " + facing + " was not preserved");
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blockEntityPersistsCoordinates(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, McGltfContent.EXPORT_WORKSTATION_BLOCK.get());
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        if (!(blockEntity instanceof ExportWorkstationBlockEntity workstation)) {
            helper.fail("no workstation block entity");
            return;
        }
        WorkstationCoordinates coordinates = new WorkstationCoordinates(
                new BlockPos(3, 4, 5), new BlockPos(-2, 80, 9));
        workstation.setCoordinates(coordinates);
        if (!coordinates.equals(workstation.coordinates())) {
            helper.fail("coordinates were not applied");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void saveReloadRoundTripsCoordinates(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, McGltfContent.EXPORT_WORKSTATION_BLOCK.get());
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        if (!(blockEntity instanceof ExportWorkstationBlockEntity workstation)) {
            helper.fail("no workstation block entity");
            return;
        }
        WorkstationCoordinates coordinates = new WorkstationCoordinates(
                new BlockPos(12, 34, 56), new BlockPos(-78, 90, 12));
        workstation.setCoordinates(coordinates);

        CompoundTag tag = workstation.saveWithFullMetadata(
                helper.getLevel().registryAccess());
        ExportWorkstationBlockEntity loaded = new ExportWorkstationBlockEntity(
                pos, helper.getBlockState(pos));
        loaded.loadWithComponents(tag, helper.getLevel().registryAccess());
        if (!coordinates.equals(loaded.coordinates())) {
            helper.fail("coordinates did not survive save/reload");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void workstationDropsItself(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, McGltfContent.EXPORT_WORKSTATION_BLOCK.get());
        helper.getLevel().destroyBlock(pos, true);
        helper.runAfterDelay(5, () -> {
            boolean dropped = helper.getLevel()
                    .getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4))
                    .stream()
                    .anyMatch(entity -> entity.getItem()
                            .is(McGltfContent.EXPORT_WORKSTATION_ITEM.get()));
            if (dropped) {
                helper.succeed();
            } else {
                helper.fail("workstation item was not dropped");
            }
        });
    }

    public static void onServerStarted(ServerStartedEvent event) {
        System.out.println("MINETOMESH_SERVER_READY");
        event.getServer().execute(() -> event.getServer().halt(false));
    }
}
