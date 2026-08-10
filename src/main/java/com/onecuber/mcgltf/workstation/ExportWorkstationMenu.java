package com.onecuber.mcgltf.workstation;

import com.onecuber.mcgltf.content.McGltfContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.IContainerFactory;

public final class ExportWorkstationMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 6;

    private final BlockPos stationPos;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public ExportWorkstationMenu(
            int containerId, Inventory inventory, ContainerLevelAccess access,
            ExportWorkstationBlockEntity blockEntity) {
        this(containerId, inventory, blockEntity.getBlockPos(), blockEntity.data(), access);
    }

    public ExportWorkstationMenu(
            int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos(),
                new SimpleContainerData(DATA_COUNT), ContainerLevelAccess.NULL);
    }

    private ExportWorkstationMenu(
            int containerId, Inventory inventory, BlockPos stationPos, ContainerData data,
            ContainerLevelAccess access) {
        super(McGltfContent.EXPORT_WORKSTATION_MENU.get(), containerId);
        this.stationPos = stationPos;
        this.data = data;
        this.access = access;
        addDataSlots(data);
    }

    public WorkstationCoordinates coordinates() {
        return new WorkstationCoordinates(
                new BlockPos(data.get(0), data.get(1), data.get(2)),
                new BlockPos(data.get(3), data.get(4), data.get(5)));
    }

    public BlockPos stationPos() {
        return stationPos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, position) ->
                level.getBlockState(position).getBlock() instanceof ExportWorkstationBlock
                        && ExportWorkstationMenu.isValidStation(
                                player.position(), position, true), true);
    }

    public static boolean isValidStation(
            Vec3 playerPosition, BlockPos stationPosition, boolean stationPresent) {
        if (!stationPresent) {
            return false;
        }
        double dx = playerPosition.x - (stationPosition.getX() + 0.5);
        double dy = playerPosition.y - (stationPosition.getY() + 0.5);
        double dz = playerPosition.z - (stationPosition.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz <= 64.0;
    }

    public static final IContainerFactory<ExportWorkstationMenu> FACTORY =
            (containerId, inventory, buffer) ->
                    new ExportWorkstationMenu(containerId, inventory, buffer);
}
