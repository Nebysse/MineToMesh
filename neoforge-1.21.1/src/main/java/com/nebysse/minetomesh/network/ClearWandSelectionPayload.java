package com.nebysse.minetomesh.network;

import com.nebysse.minetomesh.MineToMesh;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

public record ClearWandSelectionPayload(InteractionHand hand)
        implements CustomPacketPayload {
    public static final Type<ClearWandSelectionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    MineToMesh.MOD_ID, "clear_wand_selection"));
    public static final StreamCodec<FriendlyByteBuf, ClearWandSelectionPayload> STREAM_CODEC =
            StreamCodec.of(ClearWandSelectionPayload::encode,
                    ClearWandSelectionPayload::decode);

    private static void encode(
            FriendlyByteBuf buffer, ClearWandSelectionPayload payload) {
        buffer.writeEnum(payload.hand());
    }

    private static ClearWandSelectionPayload decode(FriendlyByteBuf buffer) {
        return new ClearWandSelectionPayload(
                buffer.readEnum(InteractionHand.class));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
