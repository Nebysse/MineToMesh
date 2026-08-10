package com.onecuber.mcgltf.network;

import com.onecuber.mcgltf.McGltf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ExportGrantedPayload(
        BlockPos stationPos, String exportName,
        BlockPos first, BlockPos second, String dimension)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ExportGrantedPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(McGltf.MOD_ID, "export_granted"));

    public static final StreamCodec<FriendlyByteBuf, ExportGrantedPayload> STREAM_CODEC =
            StreamCodec.of(ExportGrantedPayload::encode, ExportGrantedPayload::decode);

    private static void encode(FriendlyByteBuf buffer, ExportGrantedPayload payload) {
        buffer.writeBlockPos(payload.stationPos());
        buffer.writeUtf(payload.exportName(), 64);
        buffer.writeBlockPos(payload.first());
        buffer.writeBlockPos(payload.second());
        buffer.writeUtf(payload.dimension());
    }

    private static ExportGrantedPayload decode(FriendlyByteBuf buffer) {
        return new ExportGrantedPayload(
                buffer.readBlockPos(),
                buffer.readUtf(64),
                buffer.readBlockPos(),
                buffer.readBlockPos(),
                buffer.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
