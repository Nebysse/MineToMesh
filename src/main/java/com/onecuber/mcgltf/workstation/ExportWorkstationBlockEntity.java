package com.onecuber.mcgltf.workstation;

import com.onecuber.mcgltf.content.McGltfContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ExportWorkstationBlockEntity extends BlockEntity implements MenuProvider {
    private WorkstationCoordinates coordinates;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return ExportWorkstationBlockEntity.this.get(index);
        }

        @Override
        public void set(int index, int value) {
            ExportWorkstationBlockEntity.this.set(index, value);
        }

        @Override
        public int getCount() {
            return 6;
        }
    };

    public ExportWorkstationBlockEntity(BlockPos position, BlockState state) {
        super(McGltfContent.EXPORT_WORKSTATION_BLOCK_ENTITY.get(), position, state);
        this.coordinates = WorkstationCoordinates.at(position);
    }

    public WorkstationCoordinates coordinates() {
        return coordinates;
    }

    public void setCoordinates(WorkstationCoordinates value) {
        this.coordinates = value;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public ContainerData data() {
        return data;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.coordinates = WorkstationCoordinatesCodec.load(tag, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        WorkstationCoordinatesCodec.save(tag, coordinates);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.mcgltf.export_workstation");
    }

    @Override
    public AbstractContainerMenu createMenu(
            int containerId, Inventory inventory, Player player) {
        return new ExportWorkstationMenu(
                containerId, inventory,
                ContainerLevelAccess.create(level, worldPosition), this);
    }

    private int get(int index) {
        BlockPos position = endpoint(index) == Endpoint.FIRST
                ? coordinates.first() : coordinates.second();
        return switch (axis(index)) {
            case X -> position.getX();
            case Y -> position.getY();
            case Z -> position.getZ();
        };
    }

    private void set(int index, int value) {
        setCoordinates(coordinates.with(endpoint(index), axis(index), value));
    }

    private static Endpoint endpoint(int index) {
        return index < 3 ? Endpoint.FIRST : Endpoint.SECOND;
    }

    private static Axis axis(int index) {
        return switch (index % 3) {
            case 0 -> Axis.X;
            case 1 -> Axis.Y;
            default -> Axis.Z;
        };
    }
}
