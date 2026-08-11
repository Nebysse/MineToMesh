package com.onecuber.mcgltf.network;

import com.onecuber.mcgltf.McGltf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ExportWandRequestPayload(String exportName)
        implements CustomPacketPayload {
    public static final Type<ExportWandRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    McGltf.MOD_ID, "export_wand_request"));
    public static final StreamCodec<FriendlyByteBuf, ExportWandRequestPayload> STREAM_CODEC =
            StreamCodec.of(ExportWandRequestPayload::encode,
                    ExportWandRequestPayload::decode);

    private static void encode(
            FriendlyByteBuf buffer, ExportWandRequestPayload payload) {
        buffer.writeUtf(payload.exportName(), 64);
    }

    private static ExportWandRequestPayload decode(FriendlyByteBuf buffer) {
        return new ExportWandRequestPayload(buffer.readUtf(64));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
