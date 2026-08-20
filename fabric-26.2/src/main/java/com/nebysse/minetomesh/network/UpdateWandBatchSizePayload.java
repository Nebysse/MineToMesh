package com.nebysse.minetomesh.network;

import com.nebysse.minetomesh.MineToMeshInfo;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record UpdateWandBatchSizePayload(UUID wandId, int batchChunkCount)
        implements CustomPacketPayload {
    public static final Type<UpdateWandBatchSizePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(
                    MineToMeshInfo.MOD_ID, "update_wand_batch_size"));
    public static final StreamCodec<FriendlyByteBuf, UpdateWandBatchSizePayload> STREAM_CODEC =
            StreamCodec.of(UpdateWandBatchSizePayload::encode,
                    UpdateWandBatchSizePayload::decode);

    private static void encode(
            FriendlyByteBuf buffer, UpdateWandBatchSizePayload payload) {
        buffer.writeUUID(payload.wandId());
        buffer.writeVarInt(payload.batchChunkCount());
    }

    private static UpdateWandBatchSizePayload decode(FriendlyByteBuf buffer) {
        return new UpdateWandBatchSizePayload(
                buffer.readUUID(), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
