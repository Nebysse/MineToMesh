package com.nebysse.minetomesh.client.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nebysse.minetomesh.job.ExportOptions;
import com.nebysse.minetomesh.job.ExportProgress;
import com.nebysse.minetomesh.job.ExportSummary;
import com.nebysse.minetomesh.job.ExportTelemetry;
import com.nebysse.minetomesh.job.JobState;
import com.nebysse.minetomesh.job.ManagedJob;
import com.nebysse.minetomesh.network.BatchCaptureCompletedPayload;
import com.nebysse.minetomesh.network.BatchClientReadablePayload;
import com.nebysse.minetomesh.network.BatchLoadStartedPayload;
import com.nebysse.minetomesh.network.BatchReadyPayload;
import com.nebysse.minetomesh.network.CancelExportRequestPayload;
import com.nebysse.minetomesh.network.ExportCancelAcknowledgedPayload;
import com.nebysse.minetomesh.network.ExportSessionAcceptedPayload;
import com.nebysse.minetomesh.network.ExportSessionFailedPayload;
import com.nebysse.minetomesh.network.ExportSessionFinishedPayload;
import com.nebysse.minetomesh.network.ExportSessionRejectedPayload;
import com.nebysse.minetomesh.output.ExportName;
import com.nebysse.minetomesh.world.ChunkCoordinate;
import com.nebysse.minetomesh.world.Selection;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class RollingSessionFlowTest {
    private static final UUID SESSION = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID WAND = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String DIMENSION = "minecraft:overworld";
    private static final List<ChunkCoordinate> BATCH = List.of(
            new ChunkCoordinate(3, -2), new ChunkCoordinate(3, -1));

    @Test
    void acceptedInitializesTotalsButStartsNoCaptureBeforeBatchReady() {
        RecordingSender sender = new RecordingSender();
        ExportWandController controller = controller(sender, Set.of(), new ArrayList<>());
        controller.bind(WAND, DIMENSION);
        controller.sessionAccepted(accepted());

        assertEquals(ExportWandController.State.WAITING_FOR_SESSION, controller.state());
        assertTrue(sender.sent.isEmpty());
    }

    @Test
    void batchReadyWaitsForReadableChunksThenAcknowledgesAndCaptures() {
        RecordingSender sender = new RecordingSender();
        List<FakeJob> jobs = new ArrayList<>();
        ExportWandController controller = controller(
                sender, Set.of("3,-2", "3,-1"), jobs);
        controller.bind(WAND, DIMENSION);
        controller.sessionAccepted(accepted());
        controller.batchLoadStarted(new BatchLoadStartedPayload(
                SESSION, WAND, DIMENSION, 0, BATCH));
        controller.batchReady(new BatchReadyPayload(SESSION, WAND, DIMENSION, 0, BATCH));

        assertEquals(ExportWandController.State.EXPORTING, controller.state());
        assertEquals(1, sender.of(BatchClientReadablePayload.class).size());
        assertEquals(1, jobs.size());
    }

    @Test
    void unreadableBatchFailsAndRequestsServerCancellation() {
        RecordingSender sender = new RecordingSender();
        ExportWandController controller = controller(sender, Set.of(), new ArrayList<>());
        controller.bind(WAND, DIMENSION);
        controller.sessionAccepted(accepted());
        controller.batchLoadStarted(new BatchLoadStartedPayload(
                SESSION, WAND, DIMENSION, 0, BATCH));
        controller.batchReady(new BatchReadyPayload(SESSION, WAND, DIMENSION, 0, BATCH));

        assertEquals(ExportWandController.State.FAILED, controller.state());
        assertEquals(1, sender.of(CancelExportRequestPayload.class).size());
    }

    @Test
    void staleSequenceIsIgnoredWithoutSideEffects() {
        RecordingSender sender = new RecordingSender();
        ExportWandController controller = controller(
                sender, Set.of("3,-2", "3,-1"), new ArrayList<>());
        controller.bind(WAND, DIMENSION);
        controller.sessionAccepted(accepted());
        controller.batchLoadStarted(new BatchLoadStartedPayload(
                SESSION, WAND, DIMENSION, 0, BATCH));
        controller.batchReady(new BatchReadyPayload(SESSION, WAND, DIMENSION, 5, BATCH));

        assertEquals(ExportWandController.State.LOADING_BATCH, controller.state());
        assertTrue(sender.sent.isEmpty());
    }

    @Test
    void captureCompletionIsSentOnlyAfterTheBatchJobFinishes() {
        RecordingSender sender = new RecordingSender();
        List<FakeJob> jobs = new ArrayList<>();
        ExportWandController controller = controller(
                sender, Set.of("3,-2", "3,-1"), jobs);
        controller.bind(WAND, DIMENSION);
        controller.sessionAccepted(accepted());
        controller.batchLoadStarted(new BatchLoadStartedPayload(
                SESSION, WAND, DIMENSION, 0, BATCH));
        controller.batchReady(new BatchReadyPayload(SESSION, WAND, DIMENSION, 0, BATCH));

        controller.tick();
        assertTrue(sender.of(BatchCaptureCompletedPayload.class).isEmpty());

        jobs.get(0).complete();
        controller.tick();
        assertEquals(1, sender.of(BatchCaptureCompletedPayload.class).size());
        assertEquals(ExportWandController.State.WAITING_FOR_CHUNKS, controller.state());
    }

    @Test
    void cancellationStaysPendingUntilServerAcknowledges() {
        RecordingSender sender = new RecordingSender();
        List<FakeJob> jobs = new ArrayList<>();
        ExportWandController controller = controller(
                sender, Set.of("3,-2", "3,-1"), jobs);
        controller.bind(WAND, DIMENSION);
        controller.sessionAccepted(accepted());
        controller.batchLoadStarted(new BatchLoadStartedPayload(
                SESSION, WAND, DIMENSION, 0, BATCH));
        controller.batchReady(new BatchReadyPayload(SESSION, WAND, DIMENSION, 0, BATCH));

        controller.requestCancel("user_cancelled");
        assertEquals(ExportWandController.State.CANCELLING, controller.state());
        assertEquals(1, sender.of(CancelExportRequestPayload.class).size());

        controller.cancelAcknowledged(new ExportCancelAcknowledgedPayload(
                SESSION, WAND, DIMENSION, 0));
        assertEquals(ExportWandController.State.CANCELLED, controller.state());
    }

    @Test
    void terminalHandshakesMapToCompletedAndFailed() {
        RecordingSender sender = new RecordingSender();
        ExportWandController controller = controller(sender, Set.of(), new ArrayList<>());
        controller.bind(WAND, DIMENSION);
        controller.sessionAccepted(accepted());
        controller.sessionFinished(new ExportSessionFinishedPayload(
                SESSION, WAND, DIMENSION, "completed"));
        assertEquals(ExportWandController.State.COMPLETED, controller.state());

        ExportWandController failed = controller(
                new RecordingSender(), Set.of(), new ArrayList<>());
        failed.bind(WAND, DIMENSION);
        failed.sessionAccepted(accepted());
        failed.sessionFailed(new ExportSessionFailedPayload(
                SESSION, WAND, DIMENSION, "chunk_timeout", 0, Optional.empty()));
        assertEquals(ExportWandController.State.FAILED, failed.state());
        assertEquals("chunk_timeout", failed.rejectionKey());
    }

    private static ExportSessionAcceptedPayload accepted() {
        return new ExportSessionAcceptedPayload(
                SESSION, WAND, DIMENSION,
                new BlockPos(0, -60, 0), new BlockPos(100, 300, 100),
                "castle", true, 4, 8, 2);
    }

    private static ExportWandController controller(
            RecordingSender sender,
            Set<String> readableChunks,
            List<FakeJob> jobs) {
        return new ExportWandController(
                (selection, name, options, telemetry) -> {
                    FakeJob job = new FakeJob();
                    jobs.add(job);
                    return job;
                },
                new FakeManager(),
                sender,
                chunk -> readableChunks.contains(chunk.x() + "," + chunk.z()));
    }

    private static final class RecordingSender
            implements ExportWandController.SessionPacketSender {
        private final List<Object> sent = new ArrayList<>();

        @Override
        public void send(BatchClientReadablePayload payload) {
            sent.add(payload);
        }

        @Override
        public void send(BatchCaptureCompletedPayload payload) {
            sent.add(payload);
        }

        @Override
        public void send(com.nebysse.minetomesh.network.ExportProgressHeartbeatPayload payload) {
            sent.add(payload);
        }

        @Override
        public void send(CancelExportRequestPayload payload) {
            sent.add(payload);
        }

        @Override
        public void send(com.nebysse.minetomesh.network.ExportClientCompletedPayload payload) {
            sent.add(payload);
        }

        <T> List<T> of(Class<T> type) {
            return sent.stream().filter(type::isInstance).map(type::cast).toList();
        }
    }

    private static final class FakeManager
            implements ExportWandController.JobManagerPort {
        private ManagedJob active;

        @Override
        public boolean start(ManagedJob job) {
            active = job;
            return true;
        }

        @Override
        public void cancel(String reason) {
            if (active != null) {
                active.cancel(reason);
            }
        }

        @Override
        public Optional<ManagedJob> activeJob() {
            return Optional.ofNullable(active);
        }

        @Override
        public void tick() {
        }
    }

    private static final class FakeJob implements ManagedJob {
        private JobState state = JobState.CAPTURING;

        void complete() {
            state = JobState.COMPLETED;
        }

        @Override
        public void tick() {
        }

        @Override
        public void cancel(String reason) {
            state = JobState.CANCELLED;
        }

        @Override
        public JobState state() {
            return state;
        }

        @Override
        public ExportProgress progress() {
            return new ExportProgress(
                    state, 0, 1, 0, Duration.ZERO, "fixture",
                    0, "idle");
        }

        @Override
        public Optional<ExportSummary> summary() {
            return state == JobState.COMPLETED
                    ? Optional.of(new ExportSummary(
                            "completed", Optional.of(Path.of("export")),
                            0, 0, 0, 0, Duration.ZERO, Optional.empty()))
                    : Optional.of(new ExportSummary(
                            "failed", Optional.empty(), 0, 0, 0, 0,
                            Duration.ZERO, Optional.of("fixture_failure")));
        }
    }
}
