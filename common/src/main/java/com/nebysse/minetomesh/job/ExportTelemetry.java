package com.nebysse.minetomesh.job;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class ExportTelemetry {
    private final AtomicReference<State> reference =
            new AtomicReference<>(State.idle());

    public enum FinalizationStep {
        PUBLISHED,
        SERVER_RESTORED,
        TRACKING_RESTORED
    }

    public void initialize(
            long totalChunks,
            long totalBatches,
            long totalPositions,
            int configuredWorkers,
            int effectiveWorkers) {
        requireNonNegative(totalChunks, "totalChunks");
        requireNonNegative(totalBatches, "totalBatches");
        requireNonNegative(totalPositions, "totalPositions");
        if (configuredWorkers < 1 || effectiveWorkers < 1) {
            throw new IllegalArgumentException("Worker counts must be positive");
        }
        reference.set(new State(
                ExportStage.PREPARING_SERVER,
                0,
                0,
                totalBatches,
                0,
                totalChunks,
                0,
                totalPositions,
                0,
                0,
                configuredWorkers,
                effectiveWorkers,
                0,
                0,
                "",
                Duration.ZERO,
                Set.of()));
    }

    public void serverPrepared() {
        reference.updateAndGet(previous -> previous.advance(
                ExportStage.SYNCHRONIZING_CHUNKS,
                ExportStage.SYNCHRONIZING_CHUNKS.startPercent()));
    }

    public void chunksSynchronized(long completed) {
        requireNonNegative(completed, "completed");
        reference.updateAndGet(previous -> previous.withSynchronizedChunks(completed));
    }

    public void positionsCaptured(long completed) {
        requireNonNegative(completed, "completed");
        reference.updateAndGet(previous -> previous.withCapturedPositions(
                completed, previous.currentObjectId));
    }

    public void positionsCaptured(long completed, String currentObjectId) {
        requireNonNegative(completed, "completed");
        Objects.requireNonNull(currentObjectId, "currentObjectId");
        reference.updateAndGet(previous -> previous.withCapturedPositions(
                completed, currentObjectId));
    }

    public void chunksProcessed(long completed) {
        requireNonNegative(completed, "completed");
        reference.updateAndGet(previous -> previous.withProcessedChunks(completed));
    }

    public void batchesPersisted(long completed) {
        requireNonNegative(completed, "completed");
        reference.updateAndGet(previous -> previous.withPersistedBatches(completed));
    }

    public void batchSequence(long sequence) {
        requireNonNegative(sequence, "sequence");
        reference.updateAndGet(previous -> previous.withBatchSequence(sequence));
    }

    public void queues(int processingQueueDepth, int writingQueueDepth) {
        requireNonNegative(processingQueueDepth, "processingQueueDepth");
        requireNonNegative(writingQueueDepth, "writingQueueDepth");
        reference.updateAndGet(previous -> previous.withQueues(
                processingQueueDepth, writingQueueDepth));
    }

    public void currentObject(String currentObjectId) {
        Objects.requireNonNull(currentObjectId, "currentObjectId");
        reference.updateAndGet(previous -> previous.withCurrentObject(currentObjectId));
    }

    public void elapsed(Duration elapsed) {
        Objects.requireNonNull(elapsed, "elapsed");
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("Elapsed duration must not be negative");
        }
        reference.updateAndGet(previous -> previous.withElapsed(elapsed));
    }

    public void finalizing() {
        reference.updateAndGet(previous -> previous.advance(
                ExportStage.FINALIZING,
                ExportStage.FINALIZING.startPercent()));
    }

    public void finalizationStep(FinalizationStep step) {
        Objects.requireNonNull(step, "step");
        reference.updateAndGet(previous -> previous.withFinalizationStep(step));
    }

    public ExportProgressSnapshot snapshot() {
        return reference.get().snapshot();
    }

    /**
     * Compatibility bridge for the pre-1.3 capture pipeline. Rolling sessions use
     * the phase-specific counter methods above.
     */
    public void capture(long completed, long total, String currentObjectId, int queueDepth) {
        requireNonNegative(completed, "completed");
        requireNonNegative(total, "total");
        requireNonNegative(queueDepth, "queueDepth");
        Objects.requireNonNull(currentObjectId, "currentObjectId");
        reference.updateAndGet(previous -> previous.withLegacyCapture(
                completed, total, currentObjectId, queueDepth));
    }

    /**
     * Compatibility bridge for the existing streaming writer. This can be
     * removed after every platform uses the rolling-session acknowledgements.
     */
    public void writerStage(WriterStage stage) {
        Objects.requireNonNull(stage, "stage");
        switch (stage) {
            // Streaming sinks start draining while capture is still running;
            // the writing band must wait until every position is captured.
            case DRAINING -> reference.updateAndGet(previous ->
                    previous.capturedPositions() >= previous.totalPositions()
                            && previous.totalPositions() > 0
                            ? previous.advance(ExportStage.WRITING, 80)
                            : previous);
            case TEXTURES, DOCUMENTS, REPORT -> finalizing();
            case COMMITTED -> finalizationStep(FinalizationStep.PUBLISHED);
        }
    }

    public enum WriterStage {
        DRAINING,
        TEXTURES,
        DOCUMENTS,
        REPORT,
        COMMITTED
    }

    private static int bandPercent(
            ExportStage stage, long completed, long total) {
        if (total == 0) {
            return stage.endPercent();
        }
        long bounded = Math.min(completed, total);
        int width = stage.endPercent() - stage.startPercent();
        int offset = (int) Math.floor((double) bounded * width / total);
        return stage.startPercent() + offset;
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private record State(
            ExportStage stage,
            int percent,
            long batchSequence,
            long totalBatches,
            long synchronizedChunks,
            long totalChunks,
            long capturedPositions,
            long totalPositions,
            long processedChunks,
            long persistedBatches,
            int configuredWorkers,
            int effectiveWorkers,
            int processingQueueDepth,
            int writingQueueDepth,
            String currentObjectId,
            Duration elapsed,
            Set<FinalizationStep> finalizationSteps) {
        private State {
            finalizationSteps = Set.copyOf(finalizationSteps);
        }

        static State idle() {
            return new State(
                    ExportStage.IDLE, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, "", Duration.ZERO, Set.of());
        }

        State advance(ExportStage candidateStage, int candidatePercent) {
            ExportStage nextStage = candidateStage.ordinal() > stage.ordinal()
                    ? candidateStage : stage;
            int nextPercent = Math.max(percent, Math.clamp(candidatePercent, 0, 100));
            return copy(nextStage, nextPercent, batchSequence,
                    synchronizedChunks, capturedPositions, processedChunks,
                    persistedBatches, processingQueueDepth, writingQueueDepth,
                    currentObjectId, elapsed, finalizationSteps);
        }

        State withSynchronizedChunks(long completed) {
            long next = Math.max(synchronizedChunks, Math.min(completed, totalChunks));
            State counted = copy(stage, percent, batchSequence,
                    next, capturedPositions, processedChunks, persistedBatches,
                    processingQueueDepth, writingQueueDepth,
                    currentObjectId, elapsed, finalizationSteps);
            return counted.advance(
                    ExportStage.SYNCHRONIZING_CHUNKS,
                    bandPercent(ExportStage.SYNCHRONIZING_CHUNKS, next, totalChunks));
        }

        State withCapturedPositions(long completed, String objectId) {
            long next = Math.max(capturedPositions, Math.min(completed, totalPositions));
            State counted = copy(stage, percent, batchSequence,
                    synchronizedChunks, next, processedChunks, persistedBatches,
                    processingQueueDepth, writingQueueDepth,
                    objectId, elapsed, finalizationSteps);
            return counted.advance(
                    ExportStage.CAPTURING,
                    bandPercent(ExportStage.CAPTURING, next, totalPositions));
        }

        State withProcessedChunks(long completed) {
            long next = Math.max(processedChunks, Math.min(completed, totalChunks));
            State counted = copy(stage, percent, batchSequence,
                    synchronizedChunks, capturedPositions, next, persistedBatches,
                    processingQueueDepth, writingQueueDepth,
                    currentObjectId, elapsed, finalizationSteps);
            return counted.advance(
                    ExportStage.PROCESSING,
                    bandPercent(ExportStage.PROCESSING, next, totalChunks));
        }

        State withPersistedBatches(long completed) {
            long boundedTotal = Math.max(totalPositions, 1);
            long next = Math.max(persistedBatches, Math.min(completed, boundedTotal));
            State counted = copy(stage, percent, batchSequence,
                    synchronizedChunks, capturedPositions, processedChunks, next,
                    processingQueueDepth, writingQueueDepth,
                    currentObjectId, elapsed, finalizationSteps);
            if (totalPositions > 0 && capturedPositions < totalPositions) {
                return counted;
            }
            return counted.advance(
                    ExportStage.WRITING,
                    bandPercent(ExportStage.WRITING, next, totalPositions));
        }

        State withBatchSequence(long sequence) {
            return copy(stage, percent, Math.max(batchSequence, sequence),
                    synchronizedChunks, capturedPositions, processedChunks,
                    persistedBatches, processingQueueDepth, writingQueueDepth,
                    currentObjectId, elapsed, finalizationSteps);
        }

        State withQueues(int processingDepth, int writingDepth) {
            return copy(stage, percent, batchSequence,
                    synchronizedChunks, capturedPositions, processedChunks,
                    persistedBatches, processingDepth, writingDepth,
                    currentObjectId, elapsed, finalizationSteps);
        }

        State withCurrentObject(String objectId) {
            return copy(stage, percent, batchSequence,
                    synchronizedChunks, capturedPositions, processedChunks,
                    persistedBatches, processingQueueDepth, writingQueueDepth,
                    objectId, elapsed, finalizationSteps);
        }

        State withElapsed(Duration candidate) {
            Duration next = candidate.compareTo(elapsed) > 0 ? candidate : elapsed;
            return copy(stage, percent, batchSequence,
                    synchronizedChunks, capturedPositions, processedChunks,
                    persistedBatches, processingQueueDepth, writingQueueDepth,
                    currentObjectId, next, finalizationSteps);
        }

        State withFinalizationStep(FinalizationStep step) {
            EnumSet<FinalizationStep> nextSteps = finalizationSteps.isEmpty()
                    ? EnumSet.noneOf(FinalizationStep.class)
                    : EnumSet.copyOf(finalizationSteps);
            nextSteps.add(step);
            int candidate = nextSteps.size() == FinalizationStep.values().length
                    ? 100 : Math.min(99, 95 + nextSteps.size() * 5 / 3);
            State counted = copy(stage, percent, batchSequence,
                    synchronizedChunks, capturedPositions, processedChunks,
                    persistedBatches, processingQueueDepth, writingQueueDepth,
                    currentObjectId, elapsed, nextSteps);
            return counted.advance(ExportStage.FINALIZING, candidate);
        }

        State withLegacyCapture(
                long completed, long total, String objectId, int queueDepth) {
            long nextTotal = Math.max(totalPositions, total);
            long nextCaptured = Math.max(capturedPositions,
                    Math.min(completed, nextTotal));
            State counted = new State(
                    stage, percent, batchSequence, totalBatches,
                    synchronizedChunks, totalChunks,
                    nextCaptured, nextTotal,
                    processedChunks, persistedBatches,
                    configuredWorkers, effectiveWorkers,
                    processingQueueDepth, queueDepth,
                    objectId, elapsed, finalizationSteps);
            return counted.advance(
                    ExportStage.CAPTURING,
                    bandPercent(ExportStage.CAPTURING, nextCaptured, nextTotal));
        }

        ExportProgressSnapshot snapshot() {
            return new ExportProgressSnapshot(
                    stage,
                    percent,
                    batchSequence,
                    totalBatches,
                    synchronizedChunks,
                    totalChunks,
                    capturedPositions,
                    totalPositions,
                    processedChunks,
                    persistedBatches,
                    configuredWorkers,
                    effectiveWorkers,
                    processingQueueDepth,
                    writingQueueDepth,
                    currentObjectId,
                    elapsed);
        }

        private State copy(
                ExportStage nextStage,
                int nextPercent,
                long nextBatchSequence,
                long nextSynchronizedChunks,
                long nextCapturedPositions,
                long nextProcessedChunks,
                long nextPersistedBatches,
                int nextProcessingQueueDepth,
                int nextWritingQueueDepth,
                String nextCurrentObjectId,
                Duration nextElapsed,
                Set<FinalizationStep> nextFinalizationSteps) {
            return new State(
                    nextStage,
                    nextPercent,
                    nextBatchSequence,
                    totalBatches,
                    nextSynchronizedChunks,
                    totalChunks,
                    nextCapturedPositions,
                    totalPositions,
                    nextProcessedChunks,
                    nextPersistedBatches,
                    configuredWorkers,
                    effectiveWorkers,
                    nextProcessingQueueDepth,
                    nextWritingQueueDepth,
                    nextCurrentObjectId,
                    nextElapsed,
                    nextFinalizationSteps);
        }
    }
}
