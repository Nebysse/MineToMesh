package com.nebysse.minetomesh.server;

import com.nebysse.minetomesh.session.RandomTickRecoveryStore;
import com.nebysse.minetomesh.session.ServerExportSessionCoordinator;
import com.nebysse.minetomesh.world.ChunkCoordinate;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class PlatformExportRuntime
        implements ServerExportSessionCoordinator.SessionRuntime {
    private static final int TICKET_LEVEL = 2;
    private static final TicketType<ExportTicketKey> MINETOMESH_TICKET =
            TicketType.create("minetomesh:export", Comparator
                    .comparing((ExportTicketKey key) -> key.sessionId().toString())
                    .thenComparingLong(key -> key.chunk().toLong()));

    private final MinecraftServer server;
    private final ServerLevel level;
    private final RandomTickRecoveryStore recoveryStore;

    public PlatformExportRuntime(MinecraftServer server, ServerLevel level) {
        this.server = Objects.requireNonNull(server, "server");
        this.level = Objects.requireNonNull(level, "level");
        Path recoveryPath = server.getServerDirectory()
                .resolve("config/minetomesh/export-session-recovery.json");
        this.recoveryStore = new RandomTickRecoveryStore(recoveryPath);
    }

    @Override
    public int readRandomTickSpeed() {
        return server.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
    }

    @Override
    public void writeRecovery(ServerExportSessionCoordinator.RecoveryRecord record)
            throws Exception {
        recoveryStore.write(record);
    }

    @Override
    public void setRandomTickSpeed(int value) {
        server.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(value, server);
    }

    @Override
    public CompletionStage<Void> loadChunks(
            UUID sessionId, List<ChunkCoordinate> chunks) {
        ServerChunkCache source = level.getChunkSource();
        List<CompletableFuture<ChunkResult<ChunkAccess>>> futures =
                new ArrayList<>(chunks.size());
        for (ChunkCoordinate chunk : chunks) {
            ChunkPos pos = new ChunkPos(chunk.x(), chunk.z());
            ExportTicketKey key = new ExportTicketKey(sessionId, pos);
            source.addRegionTicket(MINETOMESH_TICKET, pos, 2, key, false);
            futures.add(source.getChunkFuture(
                    pos.x, pos.z, ChunkStatus.FULL, true));
        }
        CompletableFuture<?>[] array = futures.toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(array).thenRun(() -> {
            for (CompletableFuture<ChunkResult<ChunkAccess>> future : futures) {
                ChunkResult<ChunkAccess> result = future.join();
                if (!result.isSuccess()) {
                    throw new IllegalStateException("Failed to load export chunk: "
                            + result.getError());
                }
            }
        });
    }

    @Override
    public void setTrackingCenter(UUID playerId, ChunkCoordinate center) {
        ServerExportSessions.overrideTrackingCenter(playerId, center);
    }

    @Override
    public void releaseChunks(UUID sessionId, List<ChunkCoordinate> chunks) {
        ServerChunkCache source = level.getChunkSource();
        for (ChunkCoordinate chunk : chunks) {
            ChunkPos pos = new ChunkPos(chunk.x(), chunk.z());
            ExportTicketKey key = new ExportTicketKey(sessionId, pos);
            source.removeRegionTicket(MINETOMESH_TICKET, pos, 2, key, false);
        }
    }

    @Override
    public void restoreTrackingCenter(UUID playerId) {
        ServerExportSessions.clearTrackingCenter(playerId);
    }

    @Override
    public void deleteRecovery() throws Exception {
        recoveryStore.delete();
    }

    public RandomTickRecoveryStore recoveryStore() {
        return recoveryStore;
    }

    public record ExportTicketKey(UUID sessionId, ChunkPos chunk) {
        public ExportTicketKey {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(chunk, "chunk");
        }
    }
}
