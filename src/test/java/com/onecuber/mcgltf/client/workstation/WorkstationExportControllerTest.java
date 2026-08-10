package com.onecuber.mcgltf.client.workstation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onecuber.mcgltf.job.ExportSummary;
import com.onecuber.mcgltf.job.ExportTelemetry;
import com.onecuber.mcgltf.job.JobState;
import com.onecuber.mcgltf.job.ManagedJob;
import com.onecuber.mcgltf.job.ExportProgress;
import com.onecuber.mcgltf.network.ExportGrantedPayload;
import com.onecuber.mcgltf.output.ExportName;
import com.onecuber.mcgltf.workstation.WorkstationCoordinates;
import java.time.Duration;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkstationExportControllerTest {
    private final BlockPos station = new BlockPos(10, 64, 10);
    private final String dimension = "minecraft:overworld";
    private FakeJobFactory factory;
    private FakeJobManager jobs;
    private WorkstationExportController controller;

    @BeforeEach
    void setUp() {
        factory = new FakeJobFactory();
        jobs = new FakeJobManager();
        controller = new WorkstationExportController(factory, jobs);
        controller.bind(station, dimension);
    }

    @Test
    void matchingOpenStationStartsAndScreenCloseCancels() {
        controller.requested("flower_factory");
        assertTrue(controller.accept(grant("flower_factory")));
        assertEquals(WorkstationExportController.State.EXPORTING, controller.state());
        controller.screenClosed();
        assertEquals(JobState.CANCELLED, factory.lastJob.state());
        assertEquals(WorkstationExportController.State.CANCELLED, controller.state());
    }

    @Test
    void lateGrantAfterScreenCloseIsRejected() {
        controller.requested("flower_factory");
        controller.screenClosed();
        assertFalse(controller.accept(grant("flower_factory")));
        assertEquals(WorkstationExportController.State.CANCELLED, controller.state());
    }

    @Test
    void wrongDimensionGrantIsRejected() {
        controller.requested("flower_factory");
        ExportGrantedPayload wrong = new ExportGrantedPayload(
                station, "flower_factory",
                new BlockPos(0, 0, 0), new BlockPos(1, 1, 1),
                "minecraft:the_nether");
        assertFalse(controller.accept(wrong));
    }

    @Test
    void concurrentActiveJobRejectsGrant() {
        jobs.externalJob = new FakeJob(JobState.CAPTURING);
        controller.requested("flower_factory");
        assertFalse(controller.accept(grant("flower_factory")));
        assertEquals(WorkstationExportController.State.FAILED, controller.state());
    }

    @Test
    void screenCloseDoesNotCancelUnrelatedActiveJob() {
        FakeJob unrelated = new FakeJob(JobState.CAPTURING);
        jobs.externalJob = unrelated;
        controller.screenClosed();
        assertEquals(JobState.CAPTURING, unrelated.state());
    }

    @Test
    void tickPublishesCompletedSummary() {
        controller.requested("flower_factory");
        controller.accept(grant("flower_factory"));
        factory.lastJob.finishCompleted();
        controller.tick();
        assertEquals(WorkstationExportController.State.COMPLETED, controller.state());
        assertTrue(controller.summary().isPresent());
    }

    private ExportGrantedPayload grant(String name) {
        return new ExportGrantedPayload(
                station, name,
                new BlockPos(0, 64, 0), new BlockPos(10, 70, 10), dimension);
    }

    private static final class FakeJobFactory implements WorkstationExportController.JobStarter {
        private FakeJob lastJob;

        @Override
        public ManagedJob start(
                WorkstationCoordinates coordinates, String dimension,
                ExportName name, ExportTelemetry telemetry) {
            lastJob = new FakeJob(JobState.CAPTURING);
            return lastJob;
        }
    }

    private static final class FakeJobManager implements WorkstationExportController.JobManagerPort {
        private FakeJob externalJob;

        @Override
        public boolean start(ManagedJob job) {
            if (externalJob != null && !externalJob.isTerminal()) {
                return false;
            }
            externalJob = (FakeJob) job;
            return true;
        }

        @Override
        public void cancel(String reason) {
            if (externalJob != null && !externalJob.isTerminal()) {
                externalJob.cancel(reason);
            }
        }

        @Override
        public Optional<ManagedJob> activeJob() {
            return Optional.ofNullable(externalJob);
        }

        @Override
        public void tick() {
        }
    }

    private static final class FakeJob implements ManagedJob {
        private JobState state;
        private final ExportSummary summary = new ExportSummary(
                "completed",
                Optional.of(java.nio.file.Path.of("fake-export")),
                1, 2, 3, 0, Duration.ZERO, Optional.empty());

        private FakeJob(JobState state) {
            this.state = state;
        }

        private void finishCompleted() {
            state = JobState.COMPLETED;
        }

        @Override
        public void tick() {
        }

        @Override
        public void cancel(String reason) {
            if (!state.isTerminal()) {
                state = JobState.CANCELLED;
            }
        }

        @Override
        public JobState state() {
            return state;
        }

        @Override
        public Optional<ExportSummary> summary() {
            return Optional.of(summary);
        }

        @Override
        public ExportProgress progress() {
            return new ExportProgress(
                    state, 0, 1, 0, Duration.ZERO, "fake", 0, "idle");
        }
    }
}
