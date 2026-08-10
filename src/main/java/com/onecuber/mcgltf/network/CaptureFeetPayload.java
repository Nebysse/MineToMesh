package com.onecuber.mcgltf.network;

import com.onecuber.mcgltf.McGltf;
import com.onecuber.mcgltf.workstation.Endpoint;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CaptureFeetPayload(BlockPos stationPos, Endpoint endpoint)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CaptureFeetPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(McGltf.MOD_ID, "capture_feet"));

    public static final StreamCodec<FriendlyByteBuf, CaptureFeetPayload> STREAM_CODEC =
            StreamCodec.of(CaptureFeetPayload::encode, CaptureFeetPayload::decode);

    private static void encode(FriendlyByteBuf buffer, CaptureFeetPayload payload) {
        buffer.writeBlockPos(payload.stationPos());
        buffer.writeVarInt(payload.endpoint().ordinal());
    }

    private static CaptureFeetPayload decode(FriendlyByteBuf buffer) {
        return new CaptureFeetPayload(
                buffer.readBlockPos(),
                Endpoint.values()[buffer.readVarInt()]);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
