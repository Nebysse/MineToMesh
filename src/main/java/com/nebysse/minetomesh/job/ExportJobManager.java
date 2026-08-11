package com.nebysse.minetomesh.job;

import com.nebysse.minetomesh.scene.ChunkBatch;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

public final class ExportJobManager {
    static final long captureBudgetMs = 6L;
    private static final int WRITER_QUEUE_CAPACITY = 2;

    private final AtomicReference<ManagedJob> activeJob = new AtomicReference<>();

    public boolean start(ManagedJob job) {
        Objects.requireNonNull(job, "job");
        while (true) {
            ManagedJob current = activeJob.get();
            if (current != null && !current.isTerminal()) {
                return false;
            }
            if (activeJob.compareAndSet(current, job)) {
                return true;
            }
        }
    }

    public void tick() {
        ManagedJob job = activeJob.get();
        if (job != null && !job.isTerminal()) {
            job.tick();
        }
    }

    public void cancel(String reason) {
        ManagedJob job = activeJob.get();
        if (job != null && !job.isTerminal()) {
            job.cancel(reason);
        }
    }

    public Optional<ExportProgress> status() {
        ManagedJob job = activeJob.get();
        return job == null ? Optional.empty() : Optional.of(job.progress());
    }

    public Optional<ManagedJob> activeJob() {
        return Optional.ofNullable(activeJob.get());
    }

    static BlockingQueue<ChunkBatch> newBatchQueue() {
        return new ArrayBlockingQueue<>(WRITER_QUEUE_CAPACITY);
    }

    static CaptureBudget newCaptureBudget() {
        return CaptureBudget.start(Duration.ofMillis(captureBudgetMs), System::nanoTime);
    }
}
