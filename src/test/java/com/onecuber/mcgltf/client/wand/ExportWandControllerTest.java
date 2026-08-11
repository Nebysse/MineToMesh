package com.onecuber.mcgltf.client.wand;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onecuber.mcgltf.job.ExportProgress;
import com.onecuber.mcgltf.job.ExportOptions;
import com.onecuber.mcgltf.job.ExportSummary;
import com.onecuber.mcgltf.job.ExportTelemetry;
import com.onecuber.mcgltf.job.JobState;
import com.onecuber.mcgltf.job.ManagedJob;
import com.onecuber.mcgltf.network.ExportWandGrantedPayload;
import com.onecuber.mcgltf.output.ExportName;
import com.onecuber.mcgltf.world.Selection;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExportWandControllerTest {
    private static final UUID WAND_ID =
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final String DIMENSION = "minecraft:overworld";

    private FakeJobFactory factory;
    private FakeJobManager jobs;
    private ExportWandController controller;

    @BeforeEach
    void setUp() {
        factory = new FakeJobFactory();
        jobs = new FakeJobManager();
        controller = new ExportWandController(factory, jobs);
        controller.bind(WAND_ID, DIMENSION);
    }

    @Test
    void matchingBoundWandStartsAndScreenCloseCancels() {
        controller.requested("flower_factory");
        assertTrue(controller.accept(grant(WAND_ID, DIMENSION)));
        assertEquals(ExportWandController.State.EXPORTING, controller.state());
        controller.screenClosed();
        assertEquals(JobState.CANCELLED, factory.lastJob.state());
        assertEquals(ExportWandController.State.CANCELLED, controller.state());
    }

    @Test
    void unboundControllerRejectsGrantWithoutThrowing() {
        ExportWandController unbound = new ExportWandController(factory, jobs);
        unbound.requested("flower_factory");
        assertFalse(assertDoesNotThrow(() ->
                unbound.accept(grant(WAND_ID, DIMENSION))));
    }

    @Test
    void wrongWandAndWrongDimensionAreRejected() {
        controller.requested("flower_factory");
        assertFalse(controller.accept(grant(
                UUID.fromString("223e4567-e89b-12d3-a456-426614174000"), DIMENSION)));
        controller.requested("flower_factory");
        assertFalse(controller.accept(grant(WAND_ID, "minecraft:the_nether")));
    }

    @Test
    void lateGrantAfterCloseIsRejected() {
        controller.requested("flower_factory");
        controller.screenClosed();
        assertFalse(controller.accept(grant(WAND_ID, DIMENSION)));
    }

    @Test
    void concurrentActiveJobFailsWithoutReplacingIt() {
        FakeJob external = new FakeJob(JobState.CAPTURING);
        jobs.externalJob = external;
        controller.requested("flower_factory");
        assertFalse(controller.accept(grant(WAND_ID, DIMENSION)));
        assertEquals(ExportWandController.State.FAILED, controller.state());
        assertEquals(external, jobs.externalJob);
    }

    @Test
    void screenCloseDoesNotCancelUnrelatedJob() {
        FakeJob external = new FakeJob(JobState.CAPTURING);
        jobs.externalJob = external;
        controller.screenClosed();
        assertEquals(JobState.CAPTURING, external.state());
    }

    @Test
    void tickPublishesCompletedSummary() {
        controller.requested("flower_factory");
        controller.accept(grant(WAND_ID, DIMENSION));
        factory.lastJob.finishCompleted();
        controller.tick();
        assertEquals(ExportWandController.State.COMPLETED, controller.state());
        assertTrue(controller.summary().isPresent());
    }

    private static ExportWandGrantedPayload grant(UUID id, String dimension) {
        return new ExportWandGrantedPayload(
                id, "flower_factory",
                new BlockPos(0, 64, 0), new BlockPos(10, 70, 10), dimension, false);
    }

    private static final class FakeJobFactory implements ExportWandController.JobStarter {
        private FakeJob lastJob;

        @Override
        public ManagedJob start(
                Selection selection, ExportName name, ExportOptions options,
                ExportTelemetry telemetry) {
            lastJob = new FakeJob(JobState.CAPTURING);
            return lastJob;
        }
    }

    private static final class FakeJobManager implements ExportWandController.JobManagerPort {
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
                "completed", Optional.of(java.nio.file.Path.of("fake-export")),
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
