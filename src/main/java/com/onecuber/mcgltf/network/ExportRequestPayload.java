package com.onecuber.mcgltf.network;

import com.onecuber.mcgltf.McGltf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ExportRequestPayload(BlockPos stationPos, String exportName)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ExportRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(McGltf.MOD_ID, "export_request"));

    public static final StreamCodec<FriendlyByteBuf, ExportRequestPayload> STREAM_CODEC =
            StreamCodec.of(ExportRequestPayload::encode, ExportRequestPayload::decode);

    private static void encode(FriendlyByteBuf buffer, ExportRequestPayload payload) {
        buffer.writeBlockPos(payload.stationPos());
        buffer.writeUtf(payload.exportName(), 64);
    }

    private static ExportRequestPayload decode(FriendlyByteBuf buffer) {
        return new ExportRequestPayload(buffer.readBlockPos(), buffer.readUtf(64));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
