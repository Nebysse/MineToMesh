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
import com.nebysse.minetomesh.network.ExportClientCompletedPayload;
import com.nebysse.minetomesh.network.ExportSessionAcceptedPayload;
import com.nebysse.minetomesh.network.ExportSessionFailedPayload;
import com.nebysse.minetomesh.network.ExportSessionFinishedPayload;
import com.nebysse.minetomesh.network.ExportSessionRejectedPayload;
import com.nebysse.minetomesh.world.ChunkCoordinate;
import com.nebysse.minetomesh.world.Selection;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
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
        Fixture fixture = new Fixture();
        fixture.controller.bind(WAND, DIMENSION);
        fixture.controller.sessionAccepted(accepted());

        assertEquals(ExportWandController.State.WAITING_FOR_SESSION,
                fixture.controller.state());
        assertTrue(fixture.sender.sent.isEmpty());
        assertTrue(fixture.rolling == null);
    }

    @Test
    void batchReadyWaitsUntilChunksBecomeReadable() {
        Fixture fixture = new Fixture();
        fixture.controller.bind(WAND, DIMENSION);
        fixture.controller.sessionAccepted(accepted());
        fixture.controller.batchLoadStarted(loadStarted(0));
        fixture.controller.batchReady(ready(0));

        assertEquals(ExportWandController.State.WAITING_FOR_CHUNKS,
                fixture.controller.state());
        assertTrue(fixture.sender.sent.isEmpty());

        fixture.controller.tick();
        assertTrue(fixture.sender.sent.isEmpty());

        fixture.readable.addAll(List.of("3,-2", "3,-1"));
        fixture.controller.tick();

        assertEquals(ExportWandController.State.EXPORTING, fixture.controller.state());
        assertEquals(1, fixture.sender.of(BatchClientReadablePayload.class).size());
        assertEquals(1, fixture.rolling.enqueued.size());
    }

    @Test
    void captureCompletionIsAcknowledgedOnlyWhenBatchUnitsAreCaptured() {
        Fixture fixture = new Fixture();
        fixture.readable.addAll(List.of("3,-2", "3,-1"));
        fixture.controller.bind(WAND, DIMENSION);
        fixture.controller.sessionAccepted(accepted());
        fixture.controller.batchLoadStarted(loadStarted(0));
        fixture.controller.batchReady(ready(0));
        fixture.controller.tick();

        fixture.controller.tick();
        assertTrue(fixture.sender.of(BatchCaptureCompletedPayload.class).isEmpty());

        fixture.rolling.captured = fixture.rolling.enqueuedUnits;
        fixture.controller.tick();
        assertEquals(1, fixture.sender.of(BatchCaptureCompletedPayload.class).size());
        assertEquals(ExportWandController.State.WAITING_FOR_CHUNKS,
                fixture.controller.state());
    }

    @Test
    void finalBatchFinishesInputAndCompletionNotifiesServer() {
        Fixture fixture = new Fixture();
        fixture.readable.addAll(List.of("3,-2", "3,-1"));
        fixture.controller.bind(WAND, DIMENSION);
        fixture.controller.sessionAccepted(accepted());
        driveBatch(fixture, 0);
        driveBatch(fixture, 1);

        assertTrue(fixture.rolling.inputFinished);
        assertEquals(ExportWandController.State.EXPORTING, fixture.controller.state());

        fixture.rolling.job.complete();
        fixture.controller.tick();
        assertEquals(1, fixture.sender.of(ExportClientCompletedPayload.class).size());
        assertEquals(ExportWandController.State.FINALIZING, fixture.controller.state());

        fixture.controller.sessionFinished(new ExportSessionFinishedPayload(
                SESSION, WAND, DIMENSION, "completed"));
        assertEquals(ExportWandController.State.COMPLETED, fixture.controller.state());
    }

    @Test
    void rejectionBeforeSessionAcceptFailsTheWaitingRequest() {
        Fixture fixture = new Fixture();
        fixture.controller.bind(WAND, DIMENSION);
        fixture.controller.requested("castle");

        fixture.controller.sessionRejected(new ExportSessionRejectedPayload(
                UUID.randomUUID(), WAND, DIMENSION,
                "minetomesh.error.session.busy"));

        assertEquals(ExportWandController.State.FAILED, fixture.controller.state());
        assertEquals("minetomesh.error.session.busy",
                fixture.controller.rejectionKey());
    }

    @Test
    void staleSequenceIsIgnoredWithoutSideEffects() {
        Fixture fixture = new Fixture();
        fixture.controller.bind(WAND, DIMENSION);
        fixture.controller.sessionAccepted(accepted());
        fixture.controller.batchLoadStarted(loadStarted(0));
        fixture.controller.batchReady(ready(5));

        assertEquals(ExportWandController.State.LOADING_BATCH,
                fixture.controller.state());
        assertTrue(fixture.sender.sent.isEmpty());
    }

    @Test
    void cancellationStaysPendingUntilServerAcknowledges() {
        Fixture fixture = new Fixture();
        fixture.readable.addAll(List.of("3,-2", "3,-1"));
        fixture.controller.bind(WAND, DIMENSION);
        fixture.controller.sessionAccepted(accepted());
        fixture.controller.batchLoadStarted(loadStarted(0));
        fixture.controller.batchReady(ready(0));
        fixture.controller.tick();

        fixture.controller.requestCancel("user_cancelled");
        assertEquals(ExportWandController.State.CANCELLING, fixture.controller.state());
        assertEquals(1, fixture.sender.of(CancelExportRequestPayload.class).size());

        fixture.controller.tick();
        assertEquals(ExportWandController.State.CANCELLING, fixture.controller.state());

        fixture.controller.cancelAcknowledged(new ExportCancelAcknowledgedPayload(
                SESSION, WAND, DIMENSION, 0));
        assertEquals(ExportWandController.State.CANCELLED, fixture.controller.state());
    }

    @Test
    void serverFailureCancelsActiveCaptureAndFails() {
        Fixture fixture = new Fixture();
        fixture.readable.addAll(List.of("3,-2", "3,-1"));
        fixture.controller.bind(WAND, DIMENSION);
        fixture.controller.sessionAccepted(accepted());
        fixture.controller.batchLoadStarted(loadStarted(0));
        fixture.controller.batchReady(ready(0));
        fixture.controller.tick();

        fixture.controller.sessionFailed(new ExportSessionFailedPayload(
                SESSION, WAND, DIMENSION, "timeout:capturing", 0, Optional.empty()));

        assertEquals(ExportWandController.State.FAILED, fixture.controller.state());
        assertEquals("timeout:capturing", fixture.controller.rejectionKey());
        assertEquals(JobState.CANCELLED, fixture.rolling.job.state);
    }

    private static void driveBatch(Fixture fixture, long sequence) {
        fixture.controller.batchLoadStarted(loadStarted(sequence));
        fixture.controller.batchReady(ready(sequence));
        fixture.controller.tick();
        fixture.rolling.captured = fixture.rolling.enqueuedUnits;
        fixture.controller.tick();
    }

    private static ExportSessionAcceptedPayload accepted() {
        return new ExportSessionAcceptedPayload(
                SESSION, WAND, DIMENSION,
                new BlockPos(0, -60, 0), new BlockPos(100, 300, 100),
                "castle", true, 4, 8, 2);
    }

    private static BatchLoadStartedPayload loadStarted(long sequence) {
        return new BatchLoadStartedPayload(SESSION, WAND, DIMENSION, sequence, BATCH);
    }

    private static BatchReadyPayload ready(long sequence) {
        return new BatchReadyPayload(SESSION, WAND, DIMENSION, sequence, BATCH);
    }

    private static final class Fixture {
        private final RecordingSender sender = new RecordingSender();
        private final Set<String> readable = new HashSet<>();
        private final ExportWandController controller;
        private FakeRollingCapture rolling;

        private Fixture() {
            controller = new ExportWandController(
                    (selection, name, options, telemetry) -> {
                        throw new UnsupportedOperationException("legacy path unused");
                    },
                    new FakeManager(),
                    sender,
                    chunk -> readable.contains(chunk.x() + "," + chunk.z()),
                    (selection, name, options, telemetry) -> {
                        rolling = new FakeRollingCapture();
                        return rolling;
                    });
        }
    }

    private static final class FakeRollingCapture
            implements ExportWandController.RollingCapture {
        private final FakeJob job = new FakeJob();
        private final List<List<ChunkCoordinate>> enqueued = new ArrayList<>();
        private int enqueuedUnits;
        private int captured;
        private boolean inputFinished;

        @Override
        public ManagedJob job() {
            return job;
        }

        @Override
        public int enqueueBatch(List<ChunkCoordinate> chunks) {
            enqueued.add(chunks);
            enqueuedUnits += chunks.size() + 1;
            return chunks.size() + 1;
        }

        @Override
        public int capturedUnits() {
            return captured;
        }

        @Override
        public void finishInput() {
            inputFinished = true;
        }
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
        public void send(ExportClientCompletedPayload payload) {
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
