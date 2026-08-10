package com.onecuber.mcgltf.job;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

public final class CaptureBudget {
    private final long deadlineNanos;
    private final LongSupplier nanoTime;

    private CaptureBudget(long deadlineNanos, LongSupplier nanoTime) {
        this.deadlineNanos = deadlineNanos;
        this.nanoTime = nanoTime;
    }

    public static CaptureBudget start(Duration duration, LongSupplier nanoTime) {
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(nanoTime, "nanoTime");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Capture duration must not be negative");
        }
        long startedAt = nanoTime.getAsLong();
        long deadline = Math.addExact(startedAt, duration.toNanos());
        return new CaptureBudget(deadline, nanoTime);
    }

    public boolean hasTime() {
        return nanoTime.getAsLong() < deadlineNanos;
    }
}
