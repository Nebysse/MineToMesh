package com.onecuber.mcgltf.network;

import com.onecuber.mcgltf.McGltf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ToggleWandIncludePlayersPayload(boolean enabled)
        implements CustomPacketPayload {
    public static final Type<ToggleWandIncludePlayersPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McGltf.MOD_ID, "toggle_wand_include_players"));
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
