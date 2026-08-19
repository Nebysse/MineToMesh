package com.nebysse.minetomesh.network;

import com.nebysse.minetomesh.MineToMeshInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ToggleWandIncludePlayersPayload(boolean enabled)
        implements CustomPacketPayload {
    public static final Type<ToggleWandIncludePlayersPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(MineToMeshInfo.MOD_ID, "toggle_wand_include_players"));
    public static final StreamCodec<FriendlyByteBuf, ToggleWandIncludePlayersPayload> STREAM_CODEC =
            StreamCodec.of(ToggleWandIncludePlayersPayload::encode,
                    ToggleWandIncludePlayersPayload::decode);

    private static void encode(
            FriendlyByteBuf buffer, ToggleWandIncludePlayersPayload payload) {
        buffer.writeBoolean(payload.enabled());
    }

    private static ToggleWandIncludePlayersPayload decode(FriendlyByteBuf buffer) {
        return new ToggleWandIncludePlayersPayload(buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
