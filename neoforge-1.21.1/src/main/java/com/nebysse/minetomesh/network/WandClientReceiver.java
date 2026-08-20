package com.nebysse.minetomesh.network;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class WandClientReceiver {
    private static Consumer<ExportWandGrantedPayload> granted = value -> { };
    private static Consumer<ExportWandRejectedPayload> rejected = value -> { };
    private static Consumer<CustomPacketPayload> session = value -> { };

    private WandClientReceiver() {
    }

    public static void install(
            Consumer<ExportWandGrantedPayload> grantedHandler,
            Consumer<ExportWandRejectedPayload> rejectedHandler) {
        granted = Objects.requireNonNull(grantedHandler, "grantedHandler");
        rejected = Objects.requireNonNull(rejectedHandler, "rejectedHandler");
    }

    public static void installSessionHandler(
            Consumer<CustomPacketPayload> sessionHandler) {
        session = Objects.requireNonNull(sessionHandler, "sessionHandler");
    }

    public static void receive(ExportWandGrantedPayload payload) {
        granted.accept(payload);
    }

    public static void receive(ExportWandRejectedPayload payload) {
        rejected.accept(payload);
    }

    public static void receiveSession(CustomPacketPayload payload) {
        session.accept(Objects.requireNonNull(payload, "payload"));
    }
}
