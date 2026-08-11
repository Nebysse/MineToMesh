package com.nebysse.minetomesh.job;

import java.util.Optional;

public interface ManagedJob {
    void tick();

    void cancel(String reason);

    JobState state();

    ExportProgress progress();

    default boolean isTerminal() {
        return state().isTerminal();
    }

    default Optional<ExportSummary> summary() {
        return Optional.empty();
    }
}
