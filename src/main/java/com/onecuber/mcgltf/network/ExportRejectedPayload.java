package com.onecuber.mcgltf.network;

import com.onecuber.mcgltf.McGltf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ExportRejectedPayload(BlockPos stationPos, String reasonKey)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ExportRejectedPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(McGltf.MOD_ID, "export_rejected"));

    public static final StreamCodec<FriendlyByteBuf, ExportRejectedPayload> STREAM_CODEC =
            StreamCodec.of(ExportRejectedPayload::encode, ExportRejectedPayload::decode);

    private static void encode(FriendlyByteBuf buffer, ExportRejectedPayload payload) {
        buffer.writeBlockPos(payload.stationPos());
        buffer.writeUtf(payload.reasonKey(), 256);
    }

    private static ExportRejectedPayload decode(FriendlyByteBuf buffer) {
        return new ExportRejectedPayload(buffer.readBlockPos(), buffer.readUtf(256));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
