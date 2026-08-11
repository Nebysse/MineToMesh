package com.nebysse.minetomesh.network;

import com.nebysse.minetomesh.MineToMesh;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ToggleWandOverlayPayload(boolean enabled)
        implements CustomPacketPayload {
    public static final Type<ToggleWandOverlayPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MineToMesh.MOD_ID, "toggle_wand_overlay"));
    public static final StreamCodec<FriendlyByteBuf, ToggleWandOverlayPayload> STREAM_CODEC =
            StreamCodec.of(ToggleWandOverlayPayload::encode,
                    ToggleWandOverlayPayload::decode);

    private static void encode(
            FriendlyByteBuf buffer, ToggleWandOverlayPayload payload) {
        buffer.writeBoolean(payload.enabled());
    }

    private static ToggleWandOverlayPayload decode(FriendlyByteBuf buffer) {
        return new ToggleWandOverlayPayload(buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
