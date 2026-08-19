package com.nebysse.minetomesh.network;

import com.nebysse.minetomesh.MineToMeshInfo;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ExportWandGrantedPayload(
        UUID wandId,
        String exportName,
        BlockPos first,
        BlockPos second,
        String dimension,
        boolean includePlayers) implements CustomPacketPayload {
    public static final Type<ExportWandGrantedPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(
                    MineToMeshInfo.MOD_ID, "export_wand_granted"));
    public static final StreamCodec<FriendlyByteBuf, ExportWandGrantedPayload> STREAM_CODEC =
            StreamCodec.of(ExportWandGrantedPayload::encode,
                    ExportWandGrantedPayload::decode);

    private static void encode(
            FriendlyByteBuf buffer, ExportWandGrantedPayload payload) {
        buffer.writeUUID(payload.wandId());
        buffer.writeUtf(payload.exportName(), 64);
        buffer.writeBlockPos(payload.first());
        buffer.writeBlockPos(payload.second());
        buffer.writeUtf(payload.dimension());
        buffer.writeBoolean(payload.includePlayers());
    }

    private static ExportWandGrantedPayload decode(FriendlyByteBuf buffer) {
        return new ExportWandGrantedPayload(
                buffer.readUUID(),
                buffer.readUtf(64),
                buffer.readBlockPos(),
                buffer.readBlockPos(),
                buffer.readUtf(),
                buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
