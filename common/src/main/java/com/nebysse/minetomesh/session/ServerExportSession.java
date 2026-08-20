package com.nebysse.minetomesh.session;

import com.nebysse.minetomesh.world.ChunkBatchCursor;
import com.nebysse.minetomesh.world.ChunkCoordinate;
import com.nebysse.minetomesh.world.ChunkRange;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class ServerExportSession {
    private final UUID sessionId;
    private final UUID playerId;
    private final UUID wandId;
    private final String dimension;
    private final int batchChunkCount;
    private final long totalChunks;
    private final long totalBatches;
    private final int originalRandomTickSpeed;
    private final ChunkBatchCursor cursor;

    private ExportSessionState state = ExportSessionState.PREPARING;
    private long batchSequence;
    private List<ChunkCoordinate> currentBatch = List.of();
    private CompletableFuture<Void> loadFuture;
    private Instant deadline;

    ServerExportSession(
            UUID sessionId,
            UUID playerId,
            UUID wandId,
            String dimension,
            ChunkRange range,
            int batchChunkCount,
            int originalRandomTickSpeed) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.wandId = Objects.requireNonNull(wandId, "wandId");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.batchChunkCount = batchChunkCount;
        this.totalChunks = range.totalChunks();
        this.totalBatches = range.totalBatches(batchChunkCount);
        this.originalRandomTickSpeed = originalRandomTickSpeed;
        this.cursor = range.cursor();
    }

    void beginLoading(
            List<ChunkCoordinate> chunks,
            CompletionStage<Void> loading,
            Instant deadline) {
        transition(ExportSessionState.LOADING_BATCH);
        currentBatch = List.copyOf(chunks);
        loadFuture = Objects.requireNonNull(loading, "loading").toCompletableFuture();
        this.deadline = Objects.requireNonNull(deadline, "deadline");
    }

    void waitForClient(Instant deadline) {
        transition(ExportSessionState.WAITING_FOR_CLIENT);
        this.deadline = Objects.requireNonNull(deadline, "deadline");
    }

    void capture(Instant deadline) {
        transition(ExportSessionState.CAPTURING);
        this.deadline = Objects.requireNonNull(deadline, "deadline");
    }

    void nextBatch() {
        batchSequence = Math.addExact(batchSequence, 1L);
    }

    void finalizing(Instant deadline) {
        transition(ExportSessionState.FINALIZING);
        this.deadline = Objects.requireNonNull(deadline, "deadline");
    }

    void cleaningUp() {
        transition(ExportSessionState.CLEANING_UP);
        deadline = null;
    }

    void terminal(ExportSessionState terminal) {
        if (!terminal.isTerminal()) {
            throw new IllegalArgumentException("Terminal session state required");
        }
        transition(terminal);
    }

    void heartbeat(Instant deadline) {
        if (state != ExportSessionState.CAPTURING) {
            throw new IllegalStateException("Heartbeat requires CAPTURING state");
        }
        this.deadline = Objects.requireNonNull(deadline, "deadline");
    }

    List<ChunkCoordinate> nextChunks() {
        return cursor.next(batchChunkCount);
    }

    boolean exhausted() {
        return cursor.exhausted();
    }

    boolean matches(UUID playerId, UUID sessionId, String dimension) {
        return this.playerId.equals(playerId)
                && this.sessionId.equals(sessionId)
                && this.dimension.equals(dimension);
    }

    boolean deadlineExceeded(Instant now) {
        return deadline != null && now.isAfter(deadline);
    }

    CompletableFuture<Void> loadFuture() {
        return loadFuture;
    }

    List<ChunkCoordinate> currentBatch() {
        return currentBatch;
    }

    int originalRandomTickSpeed() {
        return originalRandomTickSpeed;
    }

    UUID sessionId() {
        return sessionId;
    }

    UUID playerId() {
        return playerId;
    }

    String dimension() {
        return dimension;
    }

    ExportSessionState state() {
        return state;
    }

    long batchSequence() {
        return batchSequence;
    }

    Snapshot snapshot() {
        return new Snapshot(
                sessionId, playerId, wandId, dimension, state,
                batchSequence, totalBatches, totalChunks,
                batchChunkCount, currentBatch, deadline);
    }

    private void transition(ExportSessionState target) {
        if (!state.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Illegal session transition " + state + " -> " + target);
        }
        state = target;
    }

    public record Snapshot(
            UUID sessionId,
            UUID playerId,
            UUID wandId,
            String dimension,
            ExportSessionState state,
            long batchSequence,
            long totalBatches,
            long totalChunks,
            int batchChunkCount,
            List<ChunkCoordinate> currentBatch,
            Instant deadline) {
        public Snapshot {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(wandId, "wandId");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(state, "state");
            currentBatch = List.copyOf(currentBatch);
        }
    }
}
