package com.nebysse.minetomesh.job;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class CancellationToken {
    private final AtomicReference<String> reason = new AtomicReference<>();

    public boolean cancel(String reason) {
        return this.reason.compareAndSet(null, Objects.requireNonNull(reason, "reason"));
    }

    public boolean isCancelled() {
        return reason.get() != null;
    }

    public Optional<String> reason() {
        return Optional.ofNullable(reason.get());
    }
}
