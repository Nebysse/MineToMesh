package com.nebysse.minetomesh.job;

import com.nebysse.minetomesh.scene.ChunkBatch;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

public final class ExportJob implements ManagedJob {
    private final CaptureSource source;
    private final RawCaptureSource rawSource;
    private final OrderedBatchExecutor processor;
    private final BatchSink sink;
    private final LongSupplier nanoTime;
    private final Duration budgetDuration;
    private final long startedAtNanos;
    private final long totalWorkItems;

    private JobState state = JobState.CAPTURING;
    private boolean entitiesCaptured;
    private int sectionIndex;
    private SectionCapture currentSection;
    private RawSectionCapture currentRawSection;
    private ChunkBatch pendingBatch;
    private RawChunkBatch pendingRawBatch;
    private OrderedBatchExecutor.CompletedBatch pendingProcessedBatch;
    private long submittedRawBatches;
    private long drainedRawBatches;
    private boolean rawSubmissionsFinished;
    private String pendingObjectId = "";
    private long completedWorkItems;
    private String currentObjectId = "entities";
    private Optional<Path> finalDirectory = Optional.empty();
    private Optional<String> failureReason = Optional.empty();
    private Optional<ExportSummary> summary = Optional.empty();
    private String outcomeStatus = "running";
    private long warningCount;
    private final ExportTelemetry telemetry;

    public ExportJob(
            CaptureSource source,
            BatchSink sink,
            LongSupplier nanoTime,
            Duration budgetDuration) {
        this(source, sink, nanoTime, budgetDuration, new ExportTelemetry());
    }

    public ExportJob(
            CaptureSource source,
            BatchSink sink,
            LongSupplier nanoTime,
            Duration budgetDuration,
            ExportTelemetry telemetry) {
        this.source = Objects.requireNonNull(source, "source");
        this.rawSource = null;
        this.processor = null;
        this.sink = Objects.requireNonNull(sink, "sink");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.budgetDuration = Objects.requireNonNull(budgetDuration, "budgetDuration");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        if (budgetDuration.isNegative() || budgetDuration.isZero()) {
            throw new IllegalArgumentException("Capture budget must be positive");
        }
        this.startedAtNanos = nanoTime.getAsLong();
        this.totalWorkItems = Math.addExact(1L, source.sectionCount());
        if (telemetry.snapshot().stage() == ExportStage.IDLE) {
            telemetry.initialize(totalWorkItems, totalWorkItems, totalWorkItems, 1, 1);
        }
    }

    public ExportJob(
            RawCaptureSource source,
            OrderedBatchExecutor processor,
            BatchSink sink,
            LongSupplier nanoTime,
            Duration budgetDuration,
            ExportTelemetry telemetry) {
        this.source = null;
        this.rawSource = Objects.requireNonNull(source, "source");
        this.processor = Objects.requireNonNull(processor, "processor");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.budgetDuration = Objects.requireNonNull(budgetDuration, "budgetDuration");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        if (budgetDuration.isNegative() || budgetDuration.isZero()) {
            throw new IllegalArgumentException("Capture budget must be positive");
        }
        this.startedAtNanos = nanoTime.getAsLong();
        this.totalWorkItems = Math.addExact(1L, source.sectionCount());
        if (telemetry.snapshot().stage() == ExportStage.IDLE) {
            telemetry.initialize(
                    totalWorkItems,
                    totalWorkItems,
                    totalWorkItems,
                    processor.workerCount(),
                    processor.workerCount());
        }
    }

    @Override
    public void tick() {
        if (processor != null) {
            tickRawPipeline();
            return;
        }
        if (state.isTerminal()) {
            return;
        }
        if (consumeWriterResult()) {
            return;
        }
        if (state == JobState.WRITING) {
            return;
        }
        CaptureBudget budget = CaptureBudget.start(budgetDuration, nanoTime);
        try {
            while (state == JobState.CAPTURING && budget.hasTime()) {
                if (pendingBatch != null) {
                    if (!sink.offer(pendingBatch)) {
                        return;
                    }
                    pendingBatch = null;
                    completedWorkItems++;
                    currentObjectId = pendingObjectId;
                    telemetry.positionsCaptured(completedWorkItems, currentObjectId);
                    pendingObjectId = "";
                    continue;
                }
                if (!entitiesCaptured) {
                    pendingBatch = source.captureEntities();
                    pendingObjectId = "entities";
                    entitiesCaptured = true;
                    continue;
                }
                if (currentSection == null) {
                    if (sectionIndex >= source.sectionCount()) {
                        if (!sink.finishInput()) {
                            return;
                        }
                        transition(JobState.WRITING);
                        return;
                    }
                    currentSection = source.openSection(sectionIndex);
                    currentObjectId = currentSection.objectId();
                }
                if (currentSection.hasNext()) {
                    currentSection.captureNext();
                    continue;
                }
                pendingBatch = currentSection.finish();
                pendingObjectId = currentSection.objectId();
                currentSection = null;
                sectionIndex++;
            }
        } catch (OutOfMemoryError error) {
            fail("Out of memory during export capture");
        } catch (Exception exception) {
            fail(exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage());
        }
    }

    private void tickRawPipeline() {
        if (state.isTerminal()) {
            return;
        }
        if (consumeWriterResult()) {
            return;
        }
        if (state == JobState.WRITING) {
            return;
        }
        CaptureBudget budget = CaptureBudget.start(budgetDuration, nanoTime);
        try {
            while (state == JobState.CAPTURING) {
                if (pendingProcessedBatch != null) {
                    if (!sink.offer(pendingProcessedBatch.batch())) {
                        return;
                    }
                    pendingProcessedBatch = null;
                    drainedRawBatches++;
                    telemetry.chunksProcessed(drainedRawBatches);
                    continue;
                }
                Optional<OrderedBatchExecutor.CompletedBatch> processed =
                        processor.pollOrdered();
                if (processed.isPresent()) {
                    pendingProcessedBatch = processed.orElseThrow();
                    continue;
                }
                if (rawSubmissionsFinished) {
                    if (drainedRawBatches < submittedRawBatches) {
                        return;
                    }
                    if (!sink.finishInput()) {
                        return;
                    }
                    transition(JobState.WRITING);
                    return;
                }
                if (pendingRawBatch != null) {
                    if (!processor.submit(pendingRawBatch)) {
                        return;
                    }
                    pendingRawBatch = null;
                    submittedRawBatches++;
                    completedWorkItems++;
                    currentObjectId = pendingObjectId;
                    telemetry.positionsCaptured(completedWorkItems, currentObjectId);
                    telemetry.queues(
                            processor.inFlightCount(), sink.queueDepth());
                    pendingObjectId = "";
                    continue;
                }
                if (!budget.hasTime()) {
                    return;
                }
                if (!entitiesCaptured) {
                    pendingRawBatch = rawSource.captureEntities();
                    pendingObjectId = "entities";
                    entitiesCaptured = true;
                    continue;
                }
                if (currentRawSection == null) {
                    if (sectionIndex >= rawSource.sectionCount()) {
                        rawSubmissionsFinished = true;
                        processor.finishSubmissions();
                        continue;
                    }
                    currentRawSection = rawSource.openSection(sectionIndex);
                    currentObjectId = currentRawSection.objectId();
                }
                if (currentRawSection.hasNext()) {
                    currentRawSection.captureNext();
                    continue;
                }
                pendingRawBatch = currentRawSection.finish();
                pendingObjectId = currentRawSection.objectId();
                currentRawSection = null;
                sectionIndex++;
            }
        } catch (OutOfMemoryError error) {
            fail("Out of memory during export capture");
        } catch (Exception exception) {
            fail(exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage());
        }
    }

    @Override
    public void cancel(String reason) {
        Objects.requireNonNull(reason, "reason");
        if (state.isTerminal()) {
            return;
        }
        if (processor != null) {
            processor.cancel(reason);
        }
        sink.cancel();
        outcomeStatus = "cancelled";
        failureReason = Optional.of(reason);
        summary = Optional.of(new ExportSummary(
                "cancelled",
                Optional.empty(),
                0,
                0,
                0,
                warningCount,
                elapsed(),
                Optional.of(reason)));
        state = JobState.CANCELLED;
    }

    @Override
    public JobState state() {
        return state;
    }

    @Override
    public ExportProgress progress() {
        ExportProgressSnapshot before = telemetry.snapshot();
        long persistedBatches = sink.persistedBatchCount();
        if (persistedBatches > 0) {
            telemetry.batchesPersisted(persistedBatches);
        }
        telemetry.queues(before.processingQueueDepth(), sink.queueDepth());
        telemetry.currentObject(currentObjectId);
        telemetry.elapsed(elapsed());
        ExportProgressSnapshot telemetrySnapshot = telemetry.snapshot();
        return new ExportProgress(
                state,
                Math.min(completedWorkItems, totalWorkItems),
                totalWorkItems,
                sink.queueDepth(),
                telemetrySnapshot.elapsed(),
                telemetrySnapshot.currentObjectId(),
                telemetrySnapshot);
    }

    @Override
    public Optional<ExportSummary> summary() {
        return summary;
    }

    private Duration elapsed() {
        long elapsedNanos = Math.max(0L, nanoTime.getAsLong() - startedAtNanos);
        return Duration.ofNanos(elapsedNanos);
    }

    public Optional<Path> finalDirectory() {
        return finalDirectory;
    }

    public Optional<String> failureReason() {
        return failureReason;
    }

    public String outcomeStatus() {
        return outcomeStatus;
    }

    public long warningCount() {
        return warningCount;
    }

    private boolean consumeWriterResult() {
        Optional<WriterResult> available = sink.pollResult();
        if (available.isEmpty()) {
            return false;
        }
        WriterResult result = available.orElseThrow();
        if (result.success()) {
            if (state != JobState.WRITING) {
                fail("Writer completed before capture reached WRITING state");
                return true;
            }
            finalDirectory = result.outputDirectory();
            warningCount = result.warningCount();
            outcomeStatus = result.status();
            summary = Optional.of(new ExportSummary(
                    result.status(),
                    result.outputDirectory(),
                    result.nodeCount(),
                    result.primitiveCount(),
                    result.textureCount(),
                    result.warningCount(),
                    elapsed(),
                    Optional.empty()));
            transition(JobState.COMPLETED);
        } else {
            failureReason = Optional.of(result.error());
            outcomeStatus = "failed";
            summary = Optional.of(new ExportSummary(
                    "failed",
                    Optional.empty(),
                    0,
                    0,
                    0,
                    warningCount,
                    elapsed(),
                    Optional.of(result.error())));
            transition(JobState.FAILED);
        }
        return true;
    }

    private void fail(String reason) {
        if (processor != null) {
            processor.cancel(reason);
        }
        sink.cancel();
        failureReason = Optional.of(reason);
        outcomeStatus = "failed";
        summary = Optional.of(new ExportSummary(
                "failed",
                Optional.empty(),
                0,
                0,
                0,
                warningCount,
                elapsed(),
                Optional.of(reason)));
        state = JobState.FAILED;
    }

    private void transition(JobState target) {
        if (!state.canTransitionTo(target)) {
            throw new IllegalStateException("Illegal job transition " + state + " -> " + target);
        }
        state = target;
    }

    public interface CaptureSource {
        ChunkBatch captureEntities() throws Exception;

        int sectionCount();

        SectionCapture openSection(int index) throws Exception;
    }

    public interface RawCaptureSource {
        RawChunkBatch captureEntities() throws Exception;

        int sectionCount();

        RawSectionCapture openSection(int index) throws Exception;
    }

    public interface SectionCapture {
        String objectId();

        boolean hasNext();

        void captureNext() throws Exception;

        ChunkBatch finish() throws Exception;
    }

    public interface RawSectionCapture {
        String objectId();

        boolean hasNext();

        void captureNext() throws Exception;

        RawChunkBatch finish() throws Exception;
    }

    public interface BatchSink {
        boolean offer(ChunkBatch batch) throws Exception;

        int queueDepth();

        boolean finishInput() throws Exception;

        Optional<WriterResult> pollResult();

        default long persistedBatchCount() {
            return 0;
        }

        void cancel();
    }

    public record WriterResult(
            boolean success,
            Optional<Path> outputDirectory,
            long warningCount,
            String status,
            String error,
            long nodeCount,
            long primitiveCount,
            long textureCount) {
        public WriterResult {
            outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(error, "error");
            if (warningCount < 0) {
                throw new IllegalArgumentException("Warning count must not be negative");
            }
            if (nodeCount < 0 || primitiveCount < 0 || textureCount < 0) {
                throw new IllegalArgumentException("Writer counts must not be negative");
            }
            if (success && outputDirectory.isEmpty()) {
                throw new IllegalArgumentException("Successful writer result requires an output directory");
            }
            if (!success && error.isBlank()) {
                throw new IllegalArgumentException("Failed writer result requires an error");
            }
        }

        public static WriterResult success(
                Path directory, long warnings, String status,
                long nodeCount, long primitiveCount, long textureCount) {
            return new WriterResult(
                    true, Optional.of(directory), warnings, status, "",
                    nodeCount, primitiveCount, textureCount);
        }

        public static WriterResult success(
                Path directory, long warnings, String status) {
            return success(directory, warnings, status, 0, 0, 0);
        }

        public static WriterResult failure(String error) {
            return new WriterResult(
                    false, Optional.empty(), 0, "failed", error, 0, 0, 0);
        }
    }
}
