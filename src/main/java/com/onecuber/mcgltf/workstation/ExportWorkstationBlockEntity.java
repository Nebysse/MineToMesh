package com.onecuber.mcgltf.workstation;

import com.onecuber.mcgltf.content.McGltfContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ExportWorkstationBlockEntity extends BlockEntity {
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
