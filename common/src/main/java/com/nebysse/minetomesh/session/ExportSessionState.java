package com.nebysse.minetomesh.session;

import java.util.Objects;

public enum ExportSessionState {
    PREPARING,
    LOADING_BATCH,
    WAITING_FOR_CLIENT,
    CAPTURING,
    FINALIZING,
    CLEANING_UP,
    COMPLETED,
    CANCELLED,
    FAILED;

    public boolean canTransitionTo(ExportSessionState target) {
        Objects.requireNonNull(target, "target");
        return switch (this) {
            case PREPARING -> target == LOADING_BATCH || target == CLEANING_UP;
            case LOADING_BATCH -> target == WAITING_FOR_CLIENT || target == CLEANING_UP;
            case WAITING_FOR_CLIENT -> target == CAPTURING || target == CLEANING_UP;
            case CAPTURING -> target == LOADING_BATCH
                    || target == FINALIZING
                    || target == CLEANING_UP;
            case FINALIZING -> target == CLEANING_UP;
            case CLEANING_UP -> target == COMPLETED
                    || target == CANCELLED
                    || target == FAILED;
            case COMPLETED, CANCELLED, FAILED -> false;
        };
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == FAILED;
    }
}
