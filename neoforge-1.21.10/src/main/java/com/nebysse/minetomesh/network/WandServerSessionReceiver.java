package com.nebysse.minetomesh.network;

import java.util.Objects;
import com.nebysse.minetomesh.wand.ExportWandSelection;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class WandServerSessionReceiver {
    private static BiConsumer<CustomPacketPayload, ServerPlayer> handler = (payload, player) -> {};
    private static Consumer<ExportRequest> exportStarter = request -> {};
    private WandServerSessionReceiver() {}

    public record ExportRequest(
            ServerPlayer player, UUID wandId, ExportWandSelection selection, String exportName) {}
    public static void install(BiConsumer<CustomPacketPayload, ServerPlayer> value) {
        handler = Objects.requireNonNull(value, "value");
    }
    public static void installExportStarter(Consumer<ExportRequest> value) {
        exportStarter = Objects.requireNonNull(value, "value");
    }
    public static void receive(CustomPacketPayload payload, ServerPlayer player) {
        handler.accept(payload, player);
    }
    public static void requestExport(ExportRequest request) {
        exportStarter.accept(Objects.requireNonNull(request, "request"));
    }
}
