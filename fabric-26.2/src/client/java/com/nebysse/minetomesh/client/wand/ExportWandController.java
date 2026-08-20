package com.nebysse.minetomesh.client.wand;

import com.nebysse.minetomesh.job.ExportJobManager;
import com.nebysse.minetomesh.job.ExportOptions;
import com.nebysse.minetomesh.job.ExportSummary;
import com.nebysse.minetomesh.job.ExportTelemetry;
import com.nebysse.minetomesh.job.ManagedJob;
import com.nebysse.minetomesh.network.BatchCaptureCompletedPayload;
import com.nebysse.minetomesh.network.BatchClientReadablePayload;
import com.nebysse.minetomesh.network.BatchLoadStartedPayload;
import com.nebysse.minetomesh.network.BatchReadyPayload;
import com.nebysse.minetomesh.network.CancelExportRequestPayload;
import com.nebysse.minetomesh.network.ExportCancelAcknowledgedPayload;
import com.nebysse.minetomesh.network.ExportClientCompletedPayload;
import com.nebysse.minetomesh.network.ExportProgressHeartbeatPayload;
import com.nebysse.minetomesh.network.ExportSessionAcceptedPayload;
import com.nebysse.minetomesh.network.ExportSessionFailedPayload;
import com.nebysse.minetomesh.network.ExportSessionFinishedPayload;
import com.nebysse.minetomesh.network.ExportSessionRejectedPayload;
import com.nebysse.minetomesh.network.ExportWandGrantedPayload;
import com.nebysse.minetomesh.network.ExportWandRejectedPayload;
import com.nebysse.minetomesh.output.ExportName;
import com.nebysse.minetomesh.world.BlockPoint;
import com.nebysse.minetomesh.world.ChunkCoordinate;
import com.nebysse.minetomesh.world.Selection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public final class ExportWandController {
    public enum State {
        READY,
        WAITING_FOR_GRANT,
        WAITING_FOR_SESSION,
        LOADING_BATCH,
        WAITING_FOR_CHUNKS,
        EXPORTING,
        FINALIZING,
        CANCELLING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public interface JobStarter {
        ManagedJob start(
                Selection selection,
                ExportName name,
                ExportOptions options,
                ExportTelemetry telemetry) throws Exception;
    }

    public interface JobManagerPort {
        boolean start(ManagedJob job);

        void cancel(String reason);

        Optional<ManagedJob> activeJob();

        void tick();
    }

    public interface SessionPacketSender {
        void send(BatchClientReadablePayload payload);

        void send(BatchCaptureCompletedPayload payload);

        void send(ExportProgressHeartbeatPayload payload);

        void send(CancelExportRequestPayload payload);

        void send(ExportClientCompletedPayload payload);
    }

    public interface ChunkReadableProbe {
        boolean isReadable(ChunkCoordinate chunk);
    }

    private final JobStarter starter;
    private final JobManagerPort jobs;
    private final SessionPacketSender sender;
    private final ChunkReadableProbe chunksReadable;
    private final ExportTelemetry telemetry = new ExportTelemetry();

    private UUID boundWandId;
    private String boundDimension;
    private State state = State.READY;
    private ManagedJob ownedJob;
    private ManagedJob batchJob;
    private Optional<ExportSummary> summary = Optional.empty();
    private String rejectionKey = "";

    private UUID sessionId;
    private String exportName = "export";
    private boolean includePlayers;
    private int batchChunkCount;
    private long totalChunks;
    private long totalBatches;
    private long batchSequence = -1;
    private List<ChunkCoordinate> currentBatch = List.of();
    private BlockPos sessionPos1;
    private BlockPos sessionPos2;
    private int workerThreads = 1;

    public void setWorkerThreads(int workerThreads) {
        if (workerThreads < 1) {
            throw new IllegalArgumentException("Worker threads must be positive");
        }
        this.workerThreads = workerThreads;
    }

    public ExportWandController(JobStarter starter, JobManagerPort jobs) {
        this(starter, jobs, new SessionPacketSender() {
            @Override public void send(BatchClientReadablePayload payload) { }
            @Override public void send(BatchCaptureCompletedPayload payload) { }
            @Override public void send(ExportProgressHeartbeatPayload payload) { }
            @Override public void send(CancelExportRequestPayload payload) { }
            @Override public void send(ExportClientCompletedPayload payload) { }
        }, chunk -> false);
    }

    public ExportWandController(
            JobStarter starter,
            JobManagerPort jobs,
            SessionPacketSender sender,
            ChunkReadableProbe chunksReadable) {
        this.starter = Objects.requireNonNull(starter, "starter");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.sender = Objects.requireNonNull(sender, "sender");
        this.chunksReadable = Objects.requireNonNull(chunksReadable, "chunksReadable");
    }

    public void bind(UUID wandId, String dimension) {
        boundWandId = Objects.requireNonNull(wandId, "wandId");
        boundDimension = Objects.requireNonNull(dimension, "dimension");
        resetSession();
        state = State.READY;
    }

    public void requested(String exportName) {
        Objects.requireNonNull(exportName, "exportName");
        resetSession();
        state = State.WAITING_FOR_GRANT;
        rejectionKey = "";
    }

    public boolean accept(ExportWandGrantedPayload grant) {
        Objects.requireNonNull(grant, "grant");
        if (state != State.WAITING_FOR_GRANT
                || boundWandId == null
                || boundDimension == null
                || !boundWandId.equals(grant.wandId())
                || !boundDimension.equals(grant.dimension())) {
            return false;
        }
        ManagedJob active = jobs.activeJob().orElse(null);
        if (active != null && !active.isTerminal()) {
            state = State.FAILED;
            rejectionKey = "minetomesh.error.wand.already_running";
            return false;
        }
        try {
            ExportName name = ExportName.parse(grant.exportName());
            Selection selection = selectionFrom(grant);
            ManagedJob job = starter.start(selection, name,
                    new ExportOptions(grant.includePlayers()), telemetry);
            if (!jobs.start(job)) {
                state = State.FAILED;
                rejectionKey = "minetomesh.error.wand.already_running";
                return false;
            }
            ownedJob = job;
            state = State.EXPORTING;
            return true;
        } catch (Exception exception) {
            state = State.FAILED;
            rejectionKey = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            return false;
        }
    }

    public void reject(ExportWandRejectedPayload payload) {
        Objects.requireNonNull(payload, "payload");
        if (state == State.WAITING_FOR_GRANT
                && boundWandId != null
                && boundWandId.equals(payload.wandId())) {
            state = State.FAILED;
            rejectionKey = payload.reasonKey();
        }
    }

    public void sessionAccepted(ExportSessionAcceptedPayload payload) {
        Objects.requireNonNull(payload, "payload");
        if (boundWandId == null
                || boundDimension == null
                || !boundWandId.equals(payload.wandId())
                || !boundDimension.equals(payload.dimension())) {
            return;
        }
        sessionId = payload.sessionId();
        exportName = payload.exportName();
        includePlayers = payload.includePlayers();
        batchChunkCount = payload.batchSize();
        totalChunks = payload.totalChunks();
        totalBatches = payload.totalBatches();
        sessionPos1 = payload.pos1();
        sessionPos2 = payload.pos2();
        batchSequence = -1;
        telemetry.initialize(
                totalChunks, totalBatches, payload.totalBatches(),
                workerThreads, workerThreads);
        state = State.WAITING_FOR_SESSION;
        rejectionKey = "";
    }

    public void sessionRejected(ExportSessionRejectedPayload payload) {
        Objects.requireNonNull(payload, "payload");
        if (!sessionMatches(payload.wandId(), payload.sessionId())) {
            return;
        }
        state = State.FAILED;
        rejectionKey = payload.reasonKey();
    }

    public void batchLoadStarted(BatchLoadStartedPayload payload) {
        Objects.requireNonNull(payload, "payload");
        if (!sessionMatches(payload.wandId(), payload.sessionId())
                || state != State.WAITING_FOR_SESSION
                && state != State.WAITING_FOR_CHUNKS) {
            return;
        }
        batchSequence = payload.batchSequence();
        currentBatch = payload.chunks();
        state = State.LOADING_BATCH;
    }

    public void batchReady(BatchReadyPayload payload) {
        Objects.requireNonNull(payload, "payload");
        if (!sessionMatches(payload.wandId(), payload.sessionId())
                || state != State.LOADING_BATCH
                || payload.batchSequence() != batchSequence) {
            return;
        }
        for (ChunkCoordinate chunk : payload.chunks()) {
            if (!chunksReadable.isReadable(chunk)) {
                state = State.FAILED;
                rejectionKey = "minetomesh.error.session.chunk_unreadable";
                sender.send(new CancelExportRequestPayload(
                        sessionId, boundWandId, boundDimension, rejectionKey));
                return;
            }
        }
        currentBatch = payload.chunks();
        state = State.WAITING_FOR_CHUNKS;
        sender.send(new BatchClientReadablePayload(
                sessionId, boundWandId, boundDimension, batchSequence));
        startBatchCapture();
    }

    private void startBatchCapture() {
        try {
            ExportName name = ExportName.parse(exportName);
            ManagedJob job = starter.start(
                    selectionForSession(),
                    name,
                    new ExportOptions(includePlayers),
                    telemetry);
            if (!jobs.start(job)) {
                failSession("minetomesh.error.wand.already_running");
                return;
            }
            batchJob = job;
            state = State.EXPORTING;
        } catch (Exception exception) {
            failSession(exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage());
        }
    }

    public void cancelAcknowledged(ExportCancelAcknowledgedPayload payload) {
        Objects.requireNonNull(payload, "payload");
        if (!sessionMatches(payload.wandId(), payload.sessionId())) {
            return;
        }
        summary = Optional.empty();
        state = State.CANCELLED;
    }

    public void sessionFinished(ExportSessionFinishedPayload payload) {
        Objects.requireNonNull(payload, "payload");
        if (!sessionMatches(payload.wandId(), payload.sessionId())) {
            return;
        }
        telemetry.finalizationStep(ExportTelemetry.FinalizationStep.PUBLISHED);
        telemetry.finalizationStep(ExportTelemetry.FinalizationStep.SERVER_RESTORED);
        telemetry.finalizationStep(ExportTelemetry.FinalizationStep.TRACKING_RESTORED);
        state = State.COMPLETED;
    }

    public void sessionFailed(ExportSessionFailedPayload payload) {
        Objects.requireNonNull(payload, "payload");
        if (!sessionMatches(payload.wandId(), payload.sessionId())) {
            return;
        }
        state = State.FAILED;
        rejectionKey = payload.reasonKey();
    }

    public void requestCancel(String reason) {
        Objects.requireNonNull(reason, "reason");
        if (sessionId == null || !isSessionActive()) {
            return;
        }
        if (batchJob != null && !batchJob.isTerminal()) {
            jobs.cancel(reason);
        }
        sender.send(new CancelExportRequestPayload(
                sessionId, boundWandId, boundDimension, reason));
        state = State.CANCELLING;
    }

    public void unbind() {
        boundWandId = null;
        boundDimension = null;
        resetSession();
        state = State.READY;
        summary = Optional.empty();
        rejectionKey = "";
    }

    public void screenClosed() {
        ManagedJob active = jobs.activeJob().orElse(null);
        if (ownedJob != null && active == ownedJob && !ownedJob.isTerminal()) {
            jobs.cancel("screen_closed");
        }
        if (batchJob != null && active == batchJob && !batchJob.isTerminal()) {
            jobs.cancel("screen_closed");
        }
        if (sessionId != null && isSessionActive()) {
            requestCancel("screen_closed");
        }
        ownedJob = null;
        batchJob = null;
        if (state == State.WAITING_FOR_GRANT
                || sessionId == null && state == State.EXPORTING) {
            state = State.CANCELLED;
        }
    }

    public void tick() {
        jobs.tick();
        if (ownedJob != null && ownedJob.isTerminal()) {
            summary = ownedJob.summary();
            state = switch (ownedJob.state()) {
                case COMPLETED -> State.COMPLETED;
                case CANCELLED -> State.CANCELLED;
                default -> State.FAILED;
            };
            ownedJob = null;
            return;
        }
        if (batchJob != null && batchJob.isTerminal()) {
            ManagedJob finished = batchJob;
            batchJob = null;
            if (finished.state() == com.nebysse.minetomesh.job.JobState.COMPLETED) {
                sender.send(new BatchCaptureCompletedPayload(
                        sessionId, boundWandId, boundDimension, batchSequence,
                        telemetry.snapshot().capturedPositions(),
                        telemetry.snapshot().processedChunks()));
                state = State.WAITING_FOR_CHUNKS;
            } else {
                failSession(finished.summary()
                        .flatMap(ExportSummary::failureReason)
                        .orElse("batch_capture_failed"));
            }
            return;
        }
        if (state == State.EXPORTING && sessionId != null) {
            sender.send(new ExportProgressHeartbeatPayload(
                    sessionId, boundWandId, boundDimension, batchSequence,
                    telemetry.snapshot().stageKey(),
                    telemetry.snapshot().capturedPositions()));
        }
    }

    public State state() {
        return state;
    }

    public Optional<ExportSummary> summary() {
        return summary;
    }

    public String rejectionKey() {
        return rejectionKey;
    }

    public ExportTelemetry telemetry() {
        return telemetry;
    }

    private boolean sessionMatches(UUID payloadWandId, UUID payloadSessionId) {
        return sessionId != null
                && boundWandId != null
                && boundWandId.equals(payloadWandId)
                && sessionId.equals(payloadSessionId);
    }

    private boolean isSessionActive() {
        return state == State.WAITING_FOR_SESSION
                || state == State.LOADING_BATCH
                || state == State.WAITING_FOR_CHUNKS
                || state == State.EXPORTING
                || state == State.FINALIZING
                || state == State.CANCELLING;
    }

    private void failSession(String reason) {
        state = State.FAILED;
        rejectionKey = reason;
        if (sessionId != null) {
            sender.send(new CancelExportRequestPayload(
                    sessionId, boundWandId, boundDimension, reason));
        }
    }

    private void resetSession() {
        sessionId = null;
        batchSequence = -1;
        currentBatch = List.of();
        sessionPos1 = null;
        sessionPos2 = null;
        batchJob = null;
        summary = Optional.empty();
    }

    private Selection selectionForSession() {
        if (sessionPos1 == null || sessionPos2 == null) {
            throw new IllegalStateException("Session selection endpoints are missing");
        }
        return Selection.of(
                blockPoint(boundDimension, sessionPos1),
                blockPoint(boundDimension, sessionPos2));
    }

    private static Selection selectionFrom(ExportWandGrantedPayload grant) {
        return Selection.of(
                blockPoint(grant.dimension(), grant.first()),
                blockPoint(grant.dimension(), grant.second()));
    }

    private static BlockPoint blockPoint(String dimension, BlockPos position) {
        return new BlockPoint(
                dimension, position.getX(), position.getY(), position.getZ());
    }

    public static JobManagerPort fromManager(ExportJobManager manager) {
        Objects.requireNonNull(manager, "manager");
        return new JobManagerPort() {
            @Override
            public boolean start(ManagedJob job) {
                return manager.start(job);
            }

            @Override
            public void cancel(String reason) {
                manager.cancel(reason);
            }

            @Override
            public Optional<ManagedJob> activeJob() {
                return manager.activeJob();
            }

            @Override
            public void tick() {
                manager.tick();
            }
        };
    }
}
