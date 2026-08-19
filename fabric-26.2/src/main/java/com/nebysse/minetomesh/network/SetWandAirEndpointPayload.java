package com.nebysse.minetomesh.network;

import com.nebysse.minetomesh.MineToMeshInfo;
import com.nebysse.minetomesh.wand.Endpoint;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;

public record SetWandAirEndpointPayload(
        InteractionHand hand, Endpoint endpoint) implements CustomPacketPayload {
    public static final Type<SetWandAirEndpointPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(
                    MineToMeshInfo.MOD_ID, "set_wand_air_endpoint"));
    public static final StreamCodec<FriendlyByteBuf, SetWandAirEndpointPayload> STREAM_CODEC =
            StreamCodec.of(SetWandAirEndpointPayload::encode,
                    SetWandAirEndpointPayload::decode);

    private static void encode(
            FriendlyByteBuf buffer, SetWandAirEndpointPayload payload) {
        buffer.writeEnum(payload.hand());
        buffer.writeEnum(payload.endpoint());
    }

    private static SetWandAirEndpointPayload decode(FriendlyByteBuf buffer) {
        return new SetWandAirEndpointPayload(
                buffer.readEnum(InteractionHand.class),
                buffer.readEnum(Endpoint.class));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
