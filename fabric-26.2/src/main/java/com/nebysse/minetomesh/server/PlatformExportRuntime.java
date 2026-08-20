package com.nebysse.minetomesh.server;

import com.nebysse.minetomesh.session.RandomTickRecoveryStore;
import com.nebysse.minetomesh.session.ServerExportSessionCoordinator;
import com.nebysse.minetomesh.world.ChunkCoordinate;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.gamerules.GameRules;

public final class PlatformExportRuntime
        implements ServerExportSessionCoordinator.SessionRuntime {
    private static final TicketType MINETOMESH_TICKET =
            new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING);

    private final MinecraftServer server;
    private final ServerLevel level;
    private final RandomTickRecoveryStore recoveryStore;

    public PlatformExportRuntime(MinecraftServer server, ServerLevel level) {
        this.server = Objects.requireNonNull(server, "server");
        this.level = Objects.requireNonNull(level, "level");
        Path recoveryPath = server.getServerDirectory()
                .resolve("config/minetomesh/export-session-recovery.json");
        recoveryStore = new RandomTickRecoveryStore(recoveryPath);
    }

    @Override
    public int readRandomTickSpeed() {
        return server.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
    }

    @Override
    public void writeRecovery(ServerExportSessionCoordinator.RecoveryRecord record)
            throws Exception {
        recoveryStore.write(record);
    }

    @Override
    public void setRandomTickSpeed(int value) {
        server.getGameRules().set(GameRules.RANDOM_TICK_SPEED, value, server);
    }

    @Override
    public CompletionStage<Void> loadChunks(
            UUID sessionId, List<ChunkCoordinate> chunks) {
        ServerChunkCache source = level.getChunkSource();
        List<CompletableFuture<?>> futures = new ArrayList<>(chunks.size());
        for (ChunkCoordinate chunk : chunks) {
            ChunkPos pos = new ChunkPos(chunk.x(), chunk.z());
            futures.add(source.addTicketAndLoadWithRadius(MINETOMESH_TICKET, pos, 0));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
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
            source.removeTicketWithRadius(MINETOMESH_TICKET, pos, 0);
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
}
