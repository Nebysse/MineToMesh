package com.nebysse.minetomesh.network;

import com.nebysse.minetomesh.world.ChunkCoordinate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

final class SessionPayloadCodecs {
    private SessionPayloadCodecs() {}

    static void identity(FriendlyByteBuf b, java.util.UUID sessionId,
                         java.util.UUID wandId, String dimension) {
        b.writeUUID(sessionId); b.writeUUID(wandId); b.writeUtf(dimension, 256);
    }

    static Identity identity(FriendlyByteBuf b) {
        return new Identity(b.readUUID(), b.readUUID(), b.readUtf(256));
    }

    static void accepted(FriendlyByteBuf b, ExportSessionAcceptedPayload p) {
        identity(b, p.sessionId(), p.wandId(), p.dimension());
        b.writeBlockPos(p.pos1()); b.writeBlockPos(p.pos2());
        b.writeUtf(p.exportName(), 64); b.writeBoolean(p.includePlayers());
        b.writeVarInt(p.batchSize()); b.writeVarLong(p.totalChunks());
        b.writeVarLong(p.totalBatches());
    }

    static ExportSessionAcceptedPayload accepted(FriendlyByteBuf b) {
        Identity i = identity(b);
        return new ExportSessionAcceptedPayload(i.sessionId, i.wandId, i.dimension,
                b.readBlockPos(), b.readBlockPos(), b.readUtf(64), b.readBoolean(),
                b.readVarInt(), b.readVarLong(), b.readVarLong());
    }

    static void rejected(FriendlyByteBuf b, ExportSessionRejectedPayload p) {
        identity(b, p.sessionId(), p.wandId(), p.dimension()); b.writeUtf(p.reasonKey(), 256);
    }

    static ExportSessionRejectedPayload rejected(FriendlyByteBuf b) {
        Identity i = identity(b);
        return new ExportSessionRejectedPayload(i.sessionId, i.wandId, i.dimension, b.readUtf(256));
    }

    static void loadStarted(FriendlyByteBuf b, BatchLoadStartedPayload p) {
        identity(b, p.sessionId(), p.wandId(), p.dimension());
        b.writeVarLong(p.batchSequence()); chunks(b, p.chunks());
    }

    static BatchLoadStartedPayload loadStarted(FriendlyByteBuf b) {
        Identity i = identity(b); long sequence = b.readVarLong();
        return new BatchLoadStartedPayload(i.sessionId, i.wandId, i.dimension, sequence, chunks(b));
    }

    static void ready(FriendlyByteBuf b, BatchReadyPayload p) {
        identity(b, p.sessionId(), p.wandId(), p.dimension());
        b.writeVarLong(p.batchSequence()); chunks(b, p.chunks());
    }

    static BatchReadyPayload ready(FriendlyByteBuf b) {
        Identity i = identity(b); long sequence = b.readVarLong();
        return new BatchReadyPayload(i.sessionId, i.wandId, i.dimension, sequence, chunks(b));
    }

    static void readable(FriendlyByteBuf b, BatchClientReadablePayload p) {
        identity(b, p.sessionId(), p.wandId(), p.dimension()); b.writeVarLong(p.batchSequence());
    }

    static BatchClientReadablePayload readable(FriendlyByteBuf b) {
        Identity i = identity(b);
        return new BatchClientReadablePayload(i.sessionId, i.wandId, i.dimension, b.readVarLong());
    }

    static void captured(FriendlyByteBuf b, BatchCaptureCompletedPayload p) {
        identity(b, p.sessionId(), p.wandId(), p.dimension());
        b.writeVarLong(p.batchSequence()); b.writeVarLong(p.capturedPositions());
        b.writeVarLong(p.processedChunks());
    }

    static BatchCaptureCompletedPayload captured(FriendlyByteBuf b) {
        Identity i = identity(b);
        return new BatchCaptureCompletedPayload(i.sessionId, i.wandId, i.dimension,
                b.readVarLong(), b.readVarLong(), b.readVarLong());
    }

    static void heartbeat(FriendlyByteBuf b, ExportProgressHeartbeatPayload p) {
        identity(b, p.sessionId(), p.wandId(), p.dimension());
        b.writeVarLong(p.batchSequence()); b.writeUtf(p.stageKey(), 256);
        b.writeVarLong(p.completedUnits());
    }

    static ExportProgressHeartbeatPayload heartbeat(FriendlyByteBuf b) {
        Identity i = identity(b);
        return new ExportProgressHeartbeatPayload(i.sessionId, i.wandId, i.dimension,
                b.readVarLong(), b.readUtf(256), b.readVarLong());
    }

    static void cancel(FriendlyByteBuf b, CancelExportRequestPayload p) {
        identity(b, p.sessionId(), p.wandId(), p.dimension()); b.writeUtf(p.reasonKey(), 256);
    }

    static CancelExportRequestPayload cancel(FriendlyByteBuf b) {
        Identity i = identity(b);
        return new CancelExportRequestPayload(i.sessionId, i.wandId, i.dimension, b.readUtf(256));
    }

    static void cancelAck(FriendlyByteBuf b, ExportCancelAcknowledgedPayload p) {
        identity(b, p.sessionId(), p.wandId(), p.dimension());
        b.writeVarInt(p.cleanupDiagnosticsCount());
    }

    static ExportCancelAcknowledgedPayload cancelAck(FriendlyByteBuf b) {
        Identity i = identity(b);
        return new ExportCancelAcknowledgedPayload(i.sessionId, i.wandId, i.dimension, b.readVarInt());
    }

    static void clientCompleted(FriendlyByteBuf b, ExportClientCompletedPayload p) {
        identity(b, p.sessionId(), p.wandId(), p.dimension());
        b.writeVarLong(p.persistedBatches()); b.writeUtf(p.status(), 128);
    }

    static ExportClientCompletedPayload clientCompleted(FriendlyByteBuf b) {
        Identity i = identity(b);
        return new ExportClientCompletedPayload(i.sessionId, i.wandId, i.dimension,
                b.readVarLong(), b.readUtf(128));
    }

    static void finished(FriendlyByteBuf b, ExportSessionFinishedPayload p) {
        identity(b, p.sessionId(), p.wandId(), p.dimension()); b.writeUtf(p.status(), 128);
    }

    static ExportSessionFinishedPayload finished(FriendlyByteBuf b) {
        Identity i = identity(b);
        return new ExportSessionFinishedPayload(i.sessionId, i.wandId, i.dimension, b.readUtf(128));
    }

    static void failed(FriendlyByteBuf b, ExportSessionFailedPayload p) {
        identity(b, p.sessionId(), p.wandId(), p.dimension());
        b.writeUtf(p.reasonKey(), 256); b.writeVarLong(p.batchSequence());
        b.writeBoolean(p.chunk().isPresent());
        p.chunk().ifPresent(c -> chunk(b, c));
    }

    static ExportSessionFailedPayload failed(FriendlyByteBuf b) {
        Identity i = identity(b); String reason = b.readUtf(256); long sequence = b.readVarLong();
        Optional<ChunkCoordinate> chunk = b.readBoolean() ? Optional.of(chunk(b)) : Optional.empty();
        return new ExportSessionFailedPayload(i.sessionId, i.wandId, i.dimension,
                reason, sequence, chunk);
    }

    private static void chunks(FriendlyByteBuf b, List<ChunkCoordinate> chunks) {
        b.writeVarInt(chunks.size());
        for (ChunkCoordinate chunk : chunks) chunk(b, chunk);
    }

    private static List<ChunkCoordinate> chunks(FriendlyByteBuf b) {
        int size = b.readVarInt();
        if (size < 0 || size > 16) throw new IllegalArgumentException("Invalid chunk batch size");
        List<ChunkCoordinate> chunks = new ArrayList<>(size);
        for (int index = 0; index < size; index++) chunks.add(chunk(b));
        return List.copyOf(chunks);
    }

    private static void chunk(FriendlyByteBuf b, ChunkCoordinate chunk) {
        b.writeInt(chunk.x()); b.writeInt(chunk.z());
    }

    private static ChunkCoordinate chunk(FriendlyByteBuf b) {
        return new ChunkCoordinate(b.readInt(), b.readInt());
    }

    private record Identity(java.util.UUID sessionId, java.util.UUID wandId, String dimension) {}
}
