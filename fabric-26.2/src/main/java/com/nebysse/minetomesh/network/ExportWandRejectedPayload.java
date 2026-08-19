package com.nebysse.minetomesh.network;

import com.nebysse.minetomesh.MineToMeshInfo;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ExportWandRejectedPayload(UUID wandId, String reasonKey)
        implements CustomPacketPayload {
    public static final Type<ExportWandRejectedPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(
                    MineToMeshInfo.MOD_ID, "export_wand_rejected"));
    public static final StreamCodec<FriendlyByteBuf, ExportWandRejectedPayload> STREAM_CODEC =
            StreamCodec.of(ExportWandRejectedPayload::encode,
                    ExportWandRejectedPayload::decode);

    private static void encode(
            FriendlyByteBuf buffer, ExportWandRejectedPayload payload) {
        buffer.writeUUID(payload.wandId());
        buffer.writeUtf(payload.reasonKey(), 256);
    }

    private static ExportWandRejectedPayload decode(FriendlyByteBuf buffer) {
        return new ExportWandRejectedPayload(
                buffer.readUUID(), buffer.readUtf(256));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
