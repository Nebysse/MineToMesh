package com.onecuber.mcgltf.network;

import com.onecuber.mcgltf.McGltf;
import com.onecuber.mcgltf.workstation.Axis;
import com.onecuber.mcgltf.workstation.Endpoint;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateCoordinatePayload(
        BlockPos stationPos, Endpoint endpoint, Axis axis, int value)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateCoordinatePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(McGltf.MOD_ID, "update_coordinate"));

    public static final StreamCodec<FriendlyByteBuf, UpdateCoordinatePayload> STREAM_CODEC =
            StreamCodec.of(UpdateCoordinatePayload::encode, UpdateCoordinatePayload::decode);

    private static void encode(FriendlyByteBuf buffer, UpdateCoordinatePayload payload) {
        buffer.writeBlockPos(payload.stationPos());
        buffer.writeVarInt(payload.endpoint().ordinal());
        buffer.writeVarInt(payload.axis().ordinal());
        buffer.writeInt(payload.value());
    }

    private static UpdateCoordinatePayload decode(FriendlyByteBuf buffer) {
        return new UpdateCoordinatePayload(
                buffer.readBlockPos(),
                Endpoint.values()[buffer.readVarInt()],
                Axis.values()[buffer.readVarInt()],
                buffer.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
