package com.onecuber.mcgltf.job;

public interface ManagedJob {
    void tick();

    void cancel(String reason);

    JobState state();

    ExportProgress progress();

    default boolean isTerminal() {
        return state().isTerminal();
    }
}
