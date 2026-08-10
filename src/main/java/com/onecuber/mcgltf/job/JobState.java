package com.onecuber.mcgltf.job;

import java.util.Objects;

public enum JobState {
    IDLE,
    PLANNING,
    CAPTURING,
    WRITING,
    COMPLETED,
    CANCELLED,
    FAILED;

    public boolean canTransitionTo(JobState target) {
        Objects.requireNonNull(target, "target");
        return switch (this) {
            case IDLE -> target == PLANNING;
            case PLANNING -> target == CAPTURING || target == CANCELLED || target == FAILED;
            case CAPTURING -> target == WRITING || target == CANCELLED || target == FAILED;
            case WRITING -> target == COMPLETED || target == CANCELLED || target == FAILED;
            case COMPLETED, CANCELLED, FAILED -> false;
        };
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == FAILED;
    }
}
