package com.onecuber.mcgltf.network;

import com.onecuber.mcgltf.McGltf;
import com.onecuber.mcgltf.wand.Endpoint;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateWandEndpointPayload(Endpoint endpoint, BlockPos position)
        implements CustomPacketPayload {
    public static final Type<UpdateWandEndpointPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McGltf.MOD_ID, "update_wand_endpoint"));
    public static final StreamCodec<FriendlyByteBuf, UpdateWandEndpointPayload> STREAM_CODEC =
            StreamCodec.of(UpdateWandEndpointPayload::encode,
                    UpdateWandEndpointPayload::decode);

    private static void encode(
            FriendlyByteBuf buffer, UpdateWandEndpointPayload payload) {
        buffer.writeEnum(payload.endpoint());
        buffer.writeBlockPos(payload.position());
    }

    private static UpdateWandEndpointPayload decode(FriendlyByteBuf buffer) {
        return new UpdateWandEndpointPayload(
                buffer.readEnum(Endpoint.class), buffer.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
