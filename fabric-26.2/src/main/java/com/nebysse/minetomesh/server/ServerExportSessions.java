package com.nebysse.minetomesh.server;

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
import com.nebysse.minetomesh.network.WandServerSessionReceiver;
import com.nebysse.minetomesh.session.ExportSessionState;
import com.nebysse.minetomesh.session.RandomTickRecoveryStore;
import com.nebysse.minetomesh.session.ServerExportSession;
import com.nebysse.minetomesh.session.ServerExportSessionCoordinator;
import com.nebysse.minetomesh.wand.ExportWandSelection;
import com.nebysse.minetomesh.world.ChunkCoordinate;
import com.nebysse.minetomesh.world.ChunkRange;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServerExportSessions {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerExportSessions.class);
    private static final Map<UUID, ChunkCoordinate> TRACKING_CENTERS =
            new ConcurrentHashMap<>();
    private static ServerExportSessionCoordinator coordinator;
    private static MinecraftServer currentServer;
    private static boolean recoveryBlocked;
    private static boolean registered;

    private ServerExportSessions() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        WandServerSessionReceiver.install(ServerExportSessions::receiveSessionPayload);
        WandServerSessionReceiver.installExportStarter(ServerExportSessions::startExport);
        ServerTickEvents.END_SERVER_TICK.register(ServerExportSessions::onServerTick);
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> onPlayerLoggedOut(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STARTED.register(ServerExportSessions::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerExportSessions::onServerStopping);
    }

    public static Optional<ChunkCoordinate> trackingCenter(UUID playerId) {
        return Optional.ofNullable(TRACKING_CENTERS.get(playerId));
    }

    static void overrideTrackingCenter(UUID playerId, ChunkCoordinate center) {
        TRACKING_CENTERS.put(playerId, center);
    }

    static void clearTrackingCenter(UUID playerId) {
        TRACKING_CENTERS.remove(playerId);
    }

    public static void onServerTick(MinecraftServer server) {
        synchronized (ServerExportSessions.class) {
            if (coordinator != null) {
                coordinator.activeSession().ifPresent(snapshot -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(snapshot.playerId());
                    if (player != null && !player.level().dimension().identifier().toString()
                            .equals(snapshot.dimension())) {
                        coordinator.cancel(snapshot.playerId(), snapshot.sessionId(),
                                snapshot.dimension(), "dimension_changed");
                    }
                });
                coordinator.tick(Instant.now());
            }
        }
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        cancelForPlayer(player.getUUID(), "player_logged_out");
    }

    public static synchronized void onServerStarted(MinecraftServer server) {
        currentServer = server;
        recoverOnStartup(currentServer);
    }

    public static synchronized void onServerStopping(MinecraftServer event) {
        if (coordinator != null) {
            coordinator.activeSession().ifPresent(snapshot -> coordinator.cancel(
                    snapshot.playerId(), snapshot.sessionId(), snapshot.dimension(),
                    "server_stopping"));
        }
        coordinator = null;
        currentServer = null;
        TRACKING_CENTERS.clear();
    }

    public static synchronized void recoverOnStartup(MinecraftServer server) {
        RandomTickRecoveryStore store = new RandomTickRecoveryStore(
                server.getServerDirectory().resolve(
                        "config/minetomesh/export-session-recovery.json"));
        RandomTickRecoveryStore.ReadResult result = store.read();
        recoveryBlocked = result.status() == RandomTickRecoveryStore.ReadStatus.CORRUPT;
        if (result.status() == RandomTickRecoveryStore.ReadStatus.VALID) {
            try {
                int value = result.record().orElseThrow().randomTickSpeed();
                server.getGameRules().set(GameRules.RANDOM_TICK_SPEED, value, server);
                store.delete();
                LOGGER.warn("Recovered MineToMesh randomTickSpeed={} after interrupted export", value);
            } catch (Exception exception) {
                recoveryBlocked = true;
                LOGGER.error("MINETOMESH_RECOVERY_FAILED", exception);
            }
        } else if (recoveryBlocked) {
            LOGGER.error("MINETOMESH_RECOVERY_CORRUPT: {}",
                    result.error().orElse("unknown"));
        }
    }

    private static synchronized void startExport(
            WandServerSessionReceiver.ExportRequest request) {
        ServerPlayer player = request.player();
        MinecraftServer server = player.level().getServer();
        if (server == null || recoveryBlocked) {
            reject(player, UUID.randomUUID(), request.wandId(),
                    request.selection(), "minetomesh.error.session.recovery_blocked");
            return;
        }
        if (coordinator != null && coordinator.activeSession().isPresent()) {
            reject(player, UUID.randomUUID(), request.wandId(), request.selection(),
                    "minetomesh.error.session.busy");
            return;
        }
        ExportWandSelection selection = request.selection();
        UUID sessionId = UUID.randomUUID();
        ChunkRange range = ChunkRange.from(selection.toSelection().orElseThrow());
        String dimension = selection.selectionDimension().orElseThrow().toString();
        PlatformExportRuntime runtime = new PlatformExportRuntime(server, player.level());
        coordinator = new ServerExportSessionCoordinator(runtime, new Messenger(server));
        // Accepted must reach the client before any batch payload, because the
        // client ignores batch traffic until the session is accepted.
        ServerPlayNetworking.send(player, new ExportSessionAcceptedPayload(
                sessionId, request.wandId(), dimension,
                selection.pos1().orElseThrow(), selection.pos2().orElseThrow(),
                request.exportName(), selection.includePlayers(),
                selection.chunkMerged(),
                selection.batchChunkCount(), range.totalChunks(),
                range.totalBatches(selection.batchChunkCount())));
        ServerExportSessionCoordinator.BeginResult result = coordinator.begin(
                new ServerExportSessionCoordinator.BeginRequest(
                        sessionId, player.getUUID(), request.wandId(),
                        dimension, range, selection.batchChunkCount()),
                Instant.now());
        if (result.status() != ServerExportSessionCoordinator.BeginStatus.STARTED) {
            reject(player, sessionId, request.wandId(), selection,
                    "minetomesh.error.session." + result.reason());
        }
    }

    private static synchronized void receiveSessionPayload(
            CustomPacketPayload payload, ServerPlayer player) {
        if (coordinator == null) return;
        Instant now = Instant.now();
        if (payload instanceof BatchClientReadablePayload value) {
            if (!matchesActiveWand(value.wandId())) return;
            coordinator.acknowledgeReadable(player.getUUID(), value.sessionId(),
                    value.dimension(), value.batchSequence(), now);
        } else if (payload instanceof BatchCaptureCompletedPayload value) {
            if (!matchesActiveWand(value.wandId())) return;
            coordinator.completeBatch(player.getUUID(), value.sessionId(),
                    value.dimension(), value.batchSequence(), now);
        } else if (payload instanceof ExportProgressHeartbeatPayload value) {
            if (!matchesActiveWand(value.wandId())) return;
            coordinator.heartbeat(player.getUUID(), value.sessionId(),
                    value.dimension(), value.batchSequence(), now);
        } else if (payload instanceof CancelExportRequestPayload value) {
            if (!matchesActiveWand(value.wandId())) return;
            coordinator.cancel(player.getUUID(), value.sessionId(),
                    value.dimension(), value.reasonKey());
        } else if (payload instanceof ExportClientCompletedPayload value) {
            if (!matchesActiveWand(value.wandId())) return;
            coordinator.clientCompleted(player.getUUID(), value.sessionId(),
                    value.dimension(), now);
        }
    }

    private static boolean matchesActiveWand(UUID wandId) {
        return coordinator != null && coordinator.activeSession()
                .filter(snapshot -> snapshot.wandId().equals(wandId)).isPresent();
    }

    private static synchronized void cancelForPlayer(UUID playerId, String reason) {
        if (coordinator == null) return;
        coordinator.activeSession().filter(snapshot -> snapshot.playerId().equals(playerId))
                .ifPresent(snapshot -> coordinator.cancel(playerId, snapshot.sessionId(),
                        snapshot.dimension(), reason));
    }

    private static void reject(ServerPlayer player, UUID sessionId, UUID wandId,
                               ExportWandSelection selection, String reason) {
        String dimension = selection.selectionDimension()
                .map(Object::toString).orElse("minecraft:overworld");
        ServerPlayNetworking.send(player,
                new ExportSessionRejectedPayload(sessionId, wandId, dimension, reason));
    }

    private static final class Messenger
            implements ServerExportSessionCoordinator.SessionMessenger {
        private final MinecraftServer server;
        private Messenger(MinecraftServer server) { this.server = server; }

        @Override
        public void batchLoadStarted(ServerExportSession.Snapshot session,
                                     List<ChunkCoordinate> chunks) {
            send(session, new BatchLoadStartedPayload(session.sessionId(), session.wandId(),
                    session.dimension(), session.batchSequence(), chunks));
        }

        @Override
        public void batchReady(ServerExportSession.Snapshot session,
                               List<ChunkCoordinate> chunks) {
            send(session, new BatchReadyPayload(session.sessionId(), session.wandId(),
                    session.dimension(), session.batchSequence(), chunks));
        }

        @Override
        public void sessionFinalizing(ServerExportSession.Snapshot session) {}

        @Override
        public void sessionFinished(ServerExportSession.Snapshot session,
                                    List<String> diagnostics) {
            if (session.state() == ExportSessionState.CANCELLED) {
                send(session, new ExportCancelAcknowledgedPayload(session.sessionId(),
                        session.wandId(), session.dimension(), diagnostics.size()));
            } else if (session.state() == ExportSessionState.COMPLETED) {
                send(session, new ExportSessionFinishedPayload(session.sessionId(),
                        session.wandId(), session.dimension(), "completed"));
            } else {
                send(session, new ExportSessionFailedPayload(session.sessionId(),
                        session.wandId(), session.dimension(),
                        diagnostics.isEmpty() ? "session_failed" : diagnostics.get(0),
                        session.batchSequence(), Optional.empty()));
            }
        }

        private void send(ServerExportSession.Snapshot session,
                          CustomPacketPayload payload) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId());
            if (player != null) ServerPlayNetworking.send(player, payload);
        }
    }
}
