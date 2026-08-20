package com.nebysse.minetomesh.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nebysse.minetomesh.world.ChunkCoordinate;
import com.nebysse.minetomesh.world.ChunkRange;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class ServerExportSessionCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");
    private static final UUID SESSION = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID WAND = UUID.fromString("30000000-0000-0000-0000-000000000003");

    @Test
    void sessionStateMachineRejectsIllegalShortcuts() {
        assertTrue(ExportSessionState.PREPARING.canTransitionTo(
                ExportSessionState.LOADING_BATCH));
        assertTrue(ExportSessionState.CAPTURING.canTransitionTo(
                ExportSessionState.FINALIZING));
        assertFalse(ExportSessionState.PREPARING.canTransitionTo(
                ExportSessionState.COMPLETED));
        assertFalse(ExportSessionState.COMPLETED.canTransitionTo(
                ExportSessionState.CAPTURING));
    }

    @Test
    void beginJournalsBeforeFreezingAndRejectsConcurrentSession() {
        FakeRuntime runtime = new FakeRuntime();
        ServerExportSessionCoordinator coordinator = coordinator(runtime);

        assertEquals(ServerExportSessionCoordinator.BeginStatus.STARTED,
                coordinator.begin(request(new ChunkRange(0, 0, 0, 0), 1), NOW).status());
        assertEquals(List.of("read-rule", "write-recovery", "set-rule:0",
                "tracking:0,0", "load:1"), runtime.events);
        assertEquals(ServerExportSessionCoordinator.BeginStatus.BUSY,
                coordinator.begin(request(new ChunkRange(2, 2, 2, 2), 1), NOW).status());
    }

    @Test
    void rollsCompactBatchesAndAcceptsDuplicateAcknowledgementsIdempotently() {
        FakeRuntime runtime = new FakeRuntime();
        RecordingMessenger messenger = new RecordingMessenger();
        ServerExportSessionCoordinator coordinator =
                new ServerExportSessionCoordinator(runtime, messenger);
        coordinator.begin(request(new ChunkRange(0, 4, 0, 0), 4), NOW);
        assertEquals(4, runtime.loaded.get(0).size());

        runtime.loads.get(0).complete(null);
        coordinator.tick(NOW.plusSeconds(1));
        assertEquals(ServerExportSessionCoordinator.AckStatus.ACCEPTED,
                coordinator.acknowledgeReadable(
                        PLAYER, SESSION, "minecraft:overworld", 0, NOW.plusSeconds(2)));
        assertEquals(ServerExportSessionCoordinator.AckStatus.DUPLICATE,
                coordinator.acknowledgeReadable(
                        PLAYER, SESSION, "minecraft:overworld", 0, NOW.plusSeconds(2)));
        assertEquals(ServerExportSessionCoordinator.AckStatus.ACCEPTED,
                coordinator.completeBatch(
                        PLAYER, SESSION, "minecraft:overworld", 0, NOW.plusSeconds(3)));

        assertEquals(1, runtime.loaded.get(1).size());
        assertEquals(4, runtime.released.get(0).size());
        assertEquals(1, coordinator.activeSession().orElseThrow().batchSequence());
        assertTrue(messenger.events.contains("load:1:1"));
    }

    @Test
    void rejectsWrongIdentityAndSequenceWithoutMutation() {
        FakeRuntime runtime = new FakeRuntime();
        ServerExportSessionCoordinator coordinator = coordinator(runtime);
        coordinator.begin(request(new ChunkRange(0, 0, 0, 0), 1), NOW);
        runtime.loads.get(0).complete(null);
        coordinator.tick(NOW.plusSeconds(1));

        assertEquals(ServerExportSessionCoordinator.AckStatus.REJECTED,
                coordinator.acknowledgeReadable(
                        UUID.randomUUID(), SESSION, "minecraft:overworld", 0, NOW));
        assertEquals(ServerExportSessionCoordinator.AckStatus.REJECTED,
                coordinator.acknowledgeReadable(
                        PLAYER, SESSION, "minecraft:the_nether", 0, NOW));
        assertEquals(ServerExportSessionCoordinator.AckStatus.REJECTED,
                coordinator.acknowledgeReadable(
                        PLAYER, SESSION, "minecraft:overworld", 9, NOW));
        assertEquals(ExportSessionState.WAITING_FOR_CLIENT,
                coordinator.activeSession().orElseThrow().state());
    }

    @Test
    void timeoutRunsEveryCleanupActionEvenWhenOneFails() {
        FakeRuntime runtime = new FakeRuntime();
        runtime.failRestoreTracking = true;
        ServerExportSessionCoordinator coordinator = coordinator(runtime);
        coordinator.begin(request(new ChunkRange(0, 0, 0, 0), 1), NOW);

        coordinator.tick(NOW.plusSeconds(61));

        assertTrue(coordinator.activeSession().isEmpty());
        assertTrue(runtime.events.contains("release:1"));
        assertTrue(runtime.events.contains("restore-tracking"));
        assertTrue(runtime.events.contains("set-rule:3"));
        assertTrue(runtime.events.contains("delete-recovery"));
        assertEquals(ExportSessionState.FAILED,
                coordinator.lastTerminalSession().orElseThrow().state());
        assertEquals(1, coordinator.lastCleanupDiagnostics().size());
    }

    @Test
    void syncHeartbeatAndFinalizationDeadlinesAreEnforced() {
        FakeRuntime syncRuntime = new FakeRuntime();
        ServerExportSessionCoordinator sync = coordinator(syncRuntime);
        sync.begin(request(new ChunkRange(0, 0, 0, 0), 1), NOW);
        syncRuntime.loads.get(0).complete(null);
        sync.tick(NOW.plusSeconds(1));
        sync.tick(NOW.plusSeconds(32));
        assertEquals(ExportSessionState.FAILED,
                sync.lastTerminalSession().orElseThrow().state());

        FakeRuntime heartbeatRuntime = new FakeRuntime();
        ServerExportSessionCoordinator heartbeat = coordinator(heartbeatRuntime);
        heartbeat.begin(request(new ChunkRange(0, 0, 0, 0), 1), NOW);
        heartbeatRuntime.loads.get(0).complete(null);
        heartbeat.tick(NOW.plusSeconds(1));
        heartbeat.acknowledgeReadable(
                PLAYER, SESSION, "minecraft:overworld", 0, NOW.plusSeconds(2));
        heartbeat.tick(NOW.plusSeconds(18));
        assertEquals(ExportSessionState.FAILED,
                heartbeat.lastTerminalSession().orElseThrow().state());

        FakeRuntime finalRuntime = new FakeRuntime();
        ServerExportSessionCoordinator finalizing = coordinator(finalRuntime);
        finalizing.begin(request(new ChunkRange(0, 0, 0, 0), 1), NOW);
        finalRuntime.loads.get(0).complete(null);
        finalizing.tick(NOW.plusSeconds(1));
        finalizing.acknowledgeReadable(
                PLAYER, SESSION, "minecraft:overworld", 0, NOW.plusSeconds(2));
        finalizing.completeBatch(
                PLAYER, SESSION, "minecraft:overworld", 0, NOW.plusSeconds(3));
        finalizing.tick(NOW.plusSeconds(124));
        assertEquals(ExportSessionState.FAILED,
                finalizing.lastTerminalSession().orElseThrow().state());
    }

    @Test
    void fullHandshakeRestoresServerAndReleasesGlobalLock() {
        FakeRuntime runtime = new FakeRuntime();
        ServerExportSessionCoordinator coordinator = coordinator(runtime);
        coordinator.begin(request(new ChunkRange(0, 0, 0, 0), 16), NOW);
        runtime.loads.get(0).complete(null);
        coordinator.tick(NOW.plusSeconds(1));
        coordinator.acknowledgeReadable(
                PLAYER, SESSION, "minecraft:overworld", 0, NOW.plusSeconds(2));
        coordinator.completeBatch(
                PLAYER, SESSION, "minecraft:overworld", 0, NOW.plusSeconds(3));
        assertEquals(ExportSessionState.FINALIZING,
                coordinator.activeSession().orElseThrow().state());

        assertEquals(ServerExportSessionCoordinator.AckStatus.ACCEPTED,
                coordinator.clientCompleted(
                        PLAYER, SESSION, "minecraft:overworld", NOW.plusSeconds(4)));
        assertTrue(coordinator.activeSession().isEmpty());
        assertEquals(ExportSessionState.COMPLETED,
                coordinator.lastTerminalSession().orElseThrow().state());
        assertEquals(ServerExportSessionCoordinator.AckStatus.DUPLICATE,
                coordinator.clientCompleted(
                        PLAYER, SESSION, "minecraft:overworld", NOW.plusSeconds(5)));
        assertFalse(runtime.recoveryPresent);
        assertEquals(3, runtime.randomTickSpeed);
    }

    private static ServerExportSessionCoordinator coordinator(FakeRuntime runtime) {
        return new ServerExportSessionCoordinator(runtime, new RecordingMessenger());
    }

    private static ServerExportSessionCoordinator.BeginRequest request(
            ChunkRange range, int batchSize) {
        return new ServerExportSessionCoordinator.BeginRequest(
                SESSION, PLAYER, WAND, "minecraft:overworld", range, batchSize);
    }

    private static final class RecordingMessenger
            implements ServerExportSessionCoordinator.SessionMessenger {
        private final List<String> events = new ArrayList<>();

        @Override
        public void batchLoadStarted(
                ServerExportSession.Snapshot session, List<ChunkCoordinate> chunks) {
            events.add("load:" + session.batchSequence() + ":" + chunks.size());
        }

        @Override
        public void batchReady(
                ServerExportSession.Snapshot session, List<ChunkCoordinate> chunks) {
            events.add("ready:" + session.batchSequence() + ":" + chunks.size());
        }

        @Override
        public void sessionFinalizing(ServerExportSession.Snapshot session) {
            events.add("finalizing");
        }

        @Override
        public void sessionFinished(
                ServerExportSession.Snapshot session, List<String> diagnostics) {
            events.add("finished:" + session.state());
        }
    }

    private static final class FakeRuntime
            implements ServerExportSessionCoordinator.SessionRuntime {
        private final List<String> events = new ArrayList<>();
        private final List<List<ChunkCoordinate>> loaded = new ArrayList<>();
        private final List<List<ChunkCoordinate>> released = new ArrayList<>();
        private final List<CompletableFuture<Void>> loads = new ArrayList<>();
        private int randomTickSpeed = 3;
        private boolean recoveryPresent;
        private boolean failRestoreTracking;

        @Override
        public int readRandomTickSpeed() {
            events.add("read-rule");
            return randomTickSpeed;
        }

        @Override
        public void writeRecovery(ServerExportSessionCoordinator.RecoveryRecord record) {
            events.add("write-recovery");
            recoveryPresent = true;
        }

        @Override
        public void setRandomTickSpeed(int value) {
            events.add("set-rule:" + value);
            randomTickSpeed = value;
        }

        @Override
        public CompletionStage<Void> loadChunks(
                UUID sessionId, List<ChunkCoordinate> chunks) {
            events.add("load:" + chunks.size());
            loaded.add(List.copyOf(chunks));
            CompletableFuture<Void> future = new CompletableFuture<>();
            loads.add(future);
            return future;
        }

        @Override
        public void setTrackingCenter(
                UUID playerId, ChunkCoordinate center) {
            events.add("tracking:" + center.x() + "," + center.z());
        }

        @Override
        public void releaseChunks(
                UUID sessionId, List<ChunkCoordinate> chunks) {
            events.add("release:" + chunks.size());
            released.add(List.copyOf(chunks));
        }

        @Override
        public void restoreTrackingCenter(UUID playerId) {
            events.add("restore-tracking");
            if (failRestoreTracking) {
                throw new IllegalStateException("tracking failed");
            }
        }

        @Override
        public void deleteRecovery() {
            events.add("delete-recovery");
            recoveryPresent = false;
        }
    }
}
