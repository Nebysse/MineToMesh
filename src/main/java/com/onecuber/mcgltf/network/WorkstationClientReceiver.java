package com.onecuber.mcgltf.network;

import java.util.Objects;
import java.util.function.Consumer;

public final class WorkstationClientReceiver {
    private static Consumer<ExportGrantedPayload> granted = value -> { };
    private static Consumer<ExportRejectedPayload> rejected = value -> { };

    private WorkstationClientReceiver() {
    }

    public static void install(
            Consumer<ExportGrantedPayload> grantedHandler,
            Consumer<ExportRejectedPayload> rejectedHandler) {
        granted = Objects.requireNonNull(grantedHandler, "grantedHandler");
        rejected = Objects.requireNonNull(rejectedHandler, "rejectedHandler");
    }

    public static void receive(ExportGrantedPayload payload) {
        granted.accept(payload);
    }

    public static void receive(ExportRejectedPayload payload) {
        rejected.accept(payload);
    }

    public static void reset() {
        granted = value -> { };
        rejected = value -> { };
    }
}
