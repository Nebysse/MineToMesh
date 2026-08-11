package com.onecuber.mcgltf.network;

import java.util.Objects;
import java.util.function.Consumer;

public final class WandClientReceiver {
    private static Consumer<ExportWandGrantedPayload> granted = value -> { };
    private static Consumer<ExportWandRejectedPayload> rejected = value -> { };

    private WandClientReceiver() {
    }

    public static void install(
            Consumer<ExportWandGrantedPayload> grantedHandler,
            Consumer<ExportWandRejectedPayload> rejectedHandler) {
        granted = Objects.requireNonNull(grantedHandler, "grantedHandler");
        rejected = Objects.requireNonNull(rejectedHandler, "rejectedHandler");
    }

    public static void receive(ExportWandGrantedPayload payload) {
        granted.accept(payload);
    }

    public static void receive(ExportWandRejectedPayload payload) {
        rejected.accept(payload);
    }
}
