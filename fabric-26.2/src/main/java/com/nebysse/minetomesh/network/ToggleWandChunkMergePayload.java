package com.nebysse.minetomesh.network;

import com.nebysse.minetomesh.MineToMeshInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ToggleWandChunkMergePayload(boolean enabled)
        implements CustomPacketPayload {
    public static final Type<ToggleWandChunkMergePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(MineToMeshInfo.MOD_ID, "toggle_wand_chunk_merge"));
    public static final StreamCodec<FriendlyByteBuf, ToggleWandChunkMergePayload> STREAM_CODEC =
            StreamCodec.of(ToggleWandChunkMergePayload::encode,
                    ToggleWandChunkMergePayload::decode);

    private static void encode(
            FriendlyByteBuf buffer, ToggleWandChunkMergePayload payload) {
        buffer.writeBoolean(payload.enabled());
    }

    private static ToggleWandChunkMergePayload decode(FriendlyByteBuf buffer) {
        return new ToggleWandChunkMergePayload(buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
