package com.onecuber.mcgltf.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onecuber.mcgltf.scene.ChunkBatch;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import org.junit.jupiter.api.Test;

class ExportJobManagerTest {
    @Test
    void definesOnlyLegalLifecycleTransitions() {
        assertTrue(JobState.IDLE.canTransitionTo(JobState.PLANNING));
        assertTrue(JobState.PLANNING.canTransitionTo(JobState.CAPTURING));
        assertTrue(JobState.CAPTURING.canTransitionTo(JobState.WRITING));
        assertTrue(JobState.WRITING.canTransitionTo(JobState.COMPLETED));
        assertTrue(JobState.PLANNING.canTransitionTo(JobState.CANCELLED));
        assertTrue(JobState.CAPTURING.canTransitionTo(JobState.FAILED));
        assertFalse(JobState.IDLE.canTransitionTo(JobState.COMPLETED));
        assertFalse(JobState.COMPLETED.canTransitionTo(JobState.PLANNING));
    }

    @Test
    void ownsOneActiveJobAndKeepsTerminalStatusUntilReplacement() {
        ExportJobManager manager = new ExportJobManager();
        FakeJob first = new FakeJob(JobState.CAPTURING);
        FakeJob second = new FakeJob(JobState.PLANNING);

        assertTrue(manager.start(first));
        assertFalse(manager.start(second));
        manager.tick();
        assertEquals(1, first.ticks);
        manager.cancel("user");
        manager.cancel("again");
        assertEquals(1, first.cancels);
        assertEquals(JobState.CANCELLED, manager.status().orElseThrow().state());
        manager.tick();
        assertEquals(1, first.ticks);
        assertTrue(manager.start(second));
        assertEquals(JobState.PLANNING, manager.status().orElseThrow().state());
    }

    @Test
    void createsAQueueWithCapacityExactlyTwo() {
        BlockingQueue<ChunkBatch> queue = ExportJobManager.newBatchQueue();

        assertEquals(2, queue.remainingCapacity());
    }

    private static final class FakeJob implements ManagedJob {
        private JobState state;
        private int ticks;
        private int cancels;

        private FakeJob(JobState state) {
            this.state = state;
        }

        @Override
        public void tick() {
            ticks++;
        }

        @Override
        public void cancel(String reason) {
            if (!isTerminal()) {
                cancels++;
                state = JobState.CANCELLED;
            }
        }

        @Override
        public JobState state() {
            return state;
        }

        @Override
        public ExportProgress progress() {
            return new ExportProgress(state, 1, 2, 0, Duration.ZERO, "fixture");
        }
    }
}
