package com.nebysse.minetomesh.session;

import com.nebysse.minetomesh.job.ExportExecutionPolicy;
import com.nebysse.minetomesh.world.ChunkCoordinate;
import com.nebysse.minetomesh.world.ChunkRange;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public final class ServerExportSessionCoordinator {
    public static final Duration LOAD_TIMEOUT = Duration.ofSeconds(60);
    public static final Duration CLIENT_SYNC_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(15);
    public static final Duration FINALIZATION_TIMEOUT = Duration.ofSeconds(120);

    public interface SessionRuntime {
        int readRandomTickSpeed();

        void writeRecovery(RecoveryRecord record) throws Exception;

        void setRandomTickSpeed(int value) throws Exception;

        CompletionStage<Void> loadChunks(
                UUID sessionId, List<ChunkCoordinate> chunks) throws Exception;

        void setTrackingCenter(
                UUID playerId, ChunkCoordinate center) throws Exception;

        void releaseChunks(
                UUID sessionId, List<ChunkCoordinate> chunks) throws Exception;

        void restoreTrackingCenter(UUID playerId) throws Exception;

        void deleteRecovery() throws Exception;
    }

    public interface SessionMessenger {
        void batchLoadStarted(
                ServerExportSession.Snapshot session, List<ChunkCoordinate> chunks);

        void batchReady(
                ServerExportSession.Snapshot session, List<ChunkCoordinate> chunks);

        void sessionFinalizing(ServerExportSession.Snapshot session);

        void sessionFinished(
                ServerExportSession.Snapshot session, List<String> diagnostics);
    }

    public enum BeginStatus {
        STARTED,
        BUSY,
        FAILED
    }

    public enum AckStatus {
        ACCEPTED,
        DUPLICATE,
        REJECTED,
        NO_SESSION
    }

    public record BeginRequest(
            UUID sessionId,
            UUID playerId,
            UUID wandId,
            String dimension,
            ChunkRange chunks,
            int batchChunkCount) {
        public BeginRequest {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(wandId, "wandId");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(chunks, "chunks");
            ExportExecutionPolicy.validateBatchChunks(batchChunkCount);
        }
    }

    public record BeginResult(BeginStatus status, String reason) {
        public BeginResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(reason, "reason");
        }
    }

    public record RecoveryRecord(
            UUID sessionId,
            UUID playerId,
            String dimension,
            int randomTickSpeed,
            Instant createdAt) {
        public RecoveryRecord {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(createdAt, "createdAt");
            if (randomTickSpeed < 0) {
                throw new IllegalArgumentException(
                        "Random tick speed must not be negative");
            }
        }
    }

    private final SessionRuntime runtime;
    private final SessionMessenger messenger;
    private ServerExportSession active;
    private ServerExportSession.Snapshot lastTerminalSession;
    private List<String> lastCleanupDiagnostics = List.of();

    public ServerExportSessionCoordinator(
            SessionRuntime runtime, SessionMessenger messenger) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.messenger = Objects.requireNonNull(messenger, "messenger");
    }

    public synchronized BeginResult begin(BeginRequest request, Instant now) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(now, "now");
        if (active != null) {
            return new BeginResult(BeginStatus.BUSY, "session_busy");
        }
        try {
            int originalRandomTickSpeed = runtime.readRandomTickSpeed();
            active = new ServerExportSession(
                    request.sessionId(), request.playerId(), request.wandId(),
                    request.dimension(), request.chunks(), request.batchChunkCount(),
                    originalRandomTickSpeed);
            runtime.writeRecovery(new RecoveryRecord(
                    request.sessionId(), request.playerId(), request.dimension(),
                    originalRandomTickSpeed, now));
            runtime.setRandomTickSpeed(0);
            startNextBatch(now);
            return new BeginResult(BeginStatus.STARTED, "");
        } catch (Exception exception) {
            if (active != null) {
                cleanup(ExportSessionState.FAILED, message(exception));
            }
            return new BeginResult(BeginStatus.FAILED, message(exception));
        }
    }

    public synchronized void tick(Instant now) {
        Objects.requireNonNull(now, "now");
        if (active == null) {
            return;
        }
        if (active.state() == ExportSessionState.LOADING_BATCH
                && active.loadFuture().isDone()) {
            try {
                active.loadFuture().join();
                active.waitForClient(now.plus(CLIENT_SYNC_TIMEOUT));
                messenger.batchReady(active.snapshot(), active.currentBatch());
            } catch (CompletionException exception) {
                cleanup(ExportSessionState.FAILED, message(exception.getCause()));
                return;
            } catch (RuntimeException exception) {
                cleanup(ExportSessionState.FAILED, message(exception));
                return;
            }
        }
        if (active != null && active.deadlineExceeded(now)) {
            cleanup(ExportSessionState.FAILED,
                    "timeout:" + active.state().name().toLowerCase());
        }
    }

    public synchronized AckStatus acknowledgeReadable(
            UUID playerId,
            UUID sessionId,
            String dimension,
            long batchSequence,
            Instant now) {
        AckStatus identity = validateIdentity(
                playerId, sessionId, dimension, batchSequence);
        if (identity != AckStatus.ACCEPTED) {
            return identity;
        }
        if (active.state() == ExportSessionState.CAPTURING) {
            return AckStatus.DUPLICATE;
        }
        if (active.state() != ExportSessionState.WAITING_FOR_CLIENT) {
            return AckStatus.REJECTED;
        }
        active.capture(Objects.requireNonNull(now, "now").plus(HEARTBEAT_TIMEOUT));
        return AckStatus.ACCEPTED;
    }

    public synchronized AckStatus heartbeat(
            UUID playerId,
            UUID sessionId,
            String dimension,
            long batchSequence,
            Instant now) {
        AckStatus identity = validateIdentity(
                playerId, sessionId, dimension, batchSequence);
        if (identity != AckStatus.ACCEPTED) {
            return identity;
        }
        if (active.state() != ExportSessionState.CAPTURING) {
            return AckStatus.REJECTED;
        }
        active.heartbeat(Objects.requireNonNull(now, "now").plus(HEARTBEAT_TIMEOUT));
        return AckStatus.ACCEPTED;
    }

    public synchronized AckStatus completeBatch(
            UUID playerId,
            UUID sessionId,
            String dimension,
            long batchSequence,
            Instant now) {
        AckStatus identity = validateIdentity(
                playerId, sessionId, dimension, batchSequence);
        if (identity != AckStatus.ACCEPTED) {
            return identity;
        }
        if (active.state() == ExportSessionState.FINALIZING) {
            return AckStatus.DUPLICATE;
        }
        if (active.state() != ExportSessionState.CAPTURING) {
            return AckStatus.REJECTED;
        }
        try {
            runtime.releaseChunks(active.sessionId(), active.currentBatch());
            if (active.exhausted()) {
                active.finalizing(Objects.requireNonNull(now, "now")
                        .plus(FINALIZATION_TIMEOUT));
                messenger.sessionFinalizing(active.snapshot());
            } else {
                active.nextBatch();
                startNextBatch(now);
            }
            return AckStatus.ACCEPTED;
        } catch (Exception exception) {
            cleanup(ExportSessionState.FAILED, message(exception));
            return AckStatus.REJECTED;
        }
    }

    public synchronized AckStatus clientCompleted(
            UUID playerId,
            UUID sessionId,
            String dimension,
            Instant now) {
        Objects.requireNonNull(now, "now");
        if (active == null) {
            return lastMatches(playerId, sessionId, dimension)
                    ? AckStatus.DUPLICATE : AckStatus.NO_SESSION;
        }
        if (!active.matches(playerId, sessionId, dimension)) {
            return AckStatus.REJECTED;
        }
        if (active.state() != ExportSessionState.FINALIZING) {
            return AckStatus.REJECTED;
        }
        cleanup(ExportSessionState.COMPLETED, "");
        return AckStatus.ACCEPTED;
    }

    public synchronized AckStatus cancel(
            UUID playerId, UUID sessionId, String dimension, String reason) {
        Objects.requireNonNull(reason, "reason");
        if (active == null) {
            return lastMatches(playerId, sessionId, dimension)
                    ? AckStatus.DUPLICATE : AckStatus.NO_SESSION;
        }
        if (!active.matches(playerId, sessionId, dimension)) {
            return AckStatus.REJECTED;
        }
        cleanup(ExportSessionState.CANCELLED, reason);
        return AckStatus.ACCEPTED;
    }

    private boolean lastMatches(
            UUID playerId, UUID sessionId, String dimension) {
        return lastTerminalSession != null
                && lastTerminalSession.playerId().equals(playerId)
                && lastTerminalSession.sessionId().equals(sessionId)
                && lastTerminalSession.dimension().equals(dimension);
    }

    public synchronized Optional<ServerExportSession.Snapshot> activeSession() {
        return active == null ? Optional.empty() : Optional.of(active.snapshot());
    }

    public synchronized Optional<ServerExportSession.Snapshot> lastTerminalSession() {
        return Optional.ofNullable(lastTerminalSession);
    }

    public synchronized List<String> lastCleanupDiagnostics() {
        return lastCleanupDiagnostics;
    }

    private AckStatus validateIdentity(
            UUID playerId,
            UUID sessionId,
            String dimension,
            long batchSequence) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(dimension, "dimension");
        if (active == null) {
            return AckStatus.NO_SESSION;
        }
        if (!active.matches(playerId, sessionId, dimension)) {
            return AckStatus.REJECTED;
        }
        if (batchSequence < active.batchSequence()) {
            return AckStatus.DUPLICATE;
        }
        return batchSequence == active.batchSequence()
                ? AckStatus.ACCEPTED : AckStatus.REJECTED;
    }

    private void startNextBatch(Instant now) throws Exception {
        List<ChunkCoordinate> chunks = active.nextChunks();
        ChunkCoordinate center = center(chunks);
        runtime.setTrackingCenter(active.playerId(), center);
        CompletionStage<Void> loading = runtime.loadChunks(active.sessionId(), chunks);
        active.beginLoading(chunks, loading, now.plus(LOAD_TIMEOUT));
        messenger.batchLoadStarted(active.snapshot(), chunks);
    }

    private void cleanup(ExportSessionState terminalState, String reason) {
        ServerExportSession session = active;
        if (session == null) {
            return;
        }
        if (session.state() != ExportSessionState.CLEANING_UP) {
            session.cleaningUp();
        }
        List<String> diagnostics = new ArrayList<>();
        guarded(diagnostics, "release_chunks", () -> runtime.releaseChunks(
                session.sessionId(), session.currentBatch()));
        guarded(diagnostics, "restore_tracking", () ->
                runtime.restoreTrackingCenter(session.playerId()));
        guarded(diagnostics, "restore_random_tick", () ->
                runtime.setRandomTickSpeed(session.originalRandomTickSpeed()));
        guarded(diagnostics, "delete_recovery", runtime::deleteRecovery);
        if (!reason.isBlank()) {
            diagnostics.add("terminal_reason:" + reason);
        }
        session.terminal(terminalState);
        lastCleanupDiagnostics = List.copyOf(diagnostics.stream()
                .filter(value -> !value.startsWith("terminal_reason:"))
                .toList());
        lastTerminalSession = session.snapshot();
        active = null;
        messenger.sessionFinished(lastTerminalSession, List.copyOf(diagnostics));
    }

    private static void guarded(
            List<String> diagnostics, String action, CheckedAction checkedAction) {
        try {
            checkedAction.run();
        } catch (Exception exception) {
            diagnostics.add(action + ":" + message(exception));
        }
    }

    private static ChunkCoordinate center(List<ChunkCoordinate> chunks) {
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Cannot center an empty chunk batch");
        }
        int minX = chunks.stream().mapToInt(ChunkCoordinate::x).min().orElseThrow();
        int maxX = chunks.stream().mapToInt(ChunkCoordinate::x).max().orElseThrow();
        int minZ = chunks.stream().mapToInt(ChunkCoordinate::z).min().orElseThrow();
        int maxZ = chunks.stream().mapToInt(ChunkCoordinate::z).max().orElseThrow();
        return new ChunkCoordinate(
                minX + Math.floorDiv(maxX - minX, 2),
                minZ + Math.floorDiv(maxZ - minZ, 2));
    }

    private static String message(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        return throwable.getMessage() == null
                ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }
}
