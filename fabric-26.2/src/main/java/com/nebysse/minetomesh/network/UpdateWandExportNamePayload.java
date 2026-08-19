package com.nebysse.minetomesh.network;

import com.nebysse.minetomesh.MineToMeshInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record UpdateWandExportNamePayload(String exportName)
        implements CustomPacketPayload {
    public static final Type<UpdateWandExportNamePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(MineToMeshInfo.MOD_ID, "update_wand_export_name"));
    public static final StreamCodec<FriendlyByteBuf, UpdateWandExportNamePayload> STREAM_CODEC =
            StreamCodec.of(UpdateWandExportNamePayload::encode,
                    UpdateWandExportNamePayload::decode);

    private static void encode(
            FriendlyByteBuf buffer, UpdateWandExportNamePayload payload) {
        buffer.writeUtf(payload.exportName(), 64);
    }

    private static UpdateWandExportNamePayload decode(FriendlyByteBuf buffer) {
        return new UpdateWandExportNamePayload(buffer.readUtf(64));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
