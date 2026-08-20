package com.nebysse.minetomesh.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nebysse.minetomesh.scene.BatchCounters;
import com.nebysse.minetomesh.scene.ChunkBatch;
import com.nebysse.minetomesh.scene.GeometryAdjustmentStats;
import com.nebysse.minetomesh.world.ChunkCoordinate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class OrderedBatchExecutorTest {
    @Test
    void outOfOrderWorkersAreDeliveredInSequenceOrder() throws Exception {
        CountDownLatch[] gates = {new CountDownLatch(1), new CountDownLatch(1)};
        try (OrderedBatchExecutor executor = new OrderedBatchExecutor(2, 2, (raw, token) -> {
            gates[(int) raw.sequence()].await();
            return processed(raw.sequence());
        })) {
            assertTrue(executor.submit(raw(0)));
            assertTrue(executor.submit(raw(1)));

            gates[1].countDown();
            awaitCompleted(executor, 1);
            assertTrue(executor.pollOrdered().isEmpty());

            gates[0].countDown();
            awaitCompleted(executor, 2);
            List<Long> sequences = new ArrayList<>();
            executor.pollOrdered().ifPresent(value -> sequences.add(value.sequence()));
            executor.pollOrdered().ifPresent(value -> sequences.add(value.sequence()));
            assertEquals(List.of(0L, 1L), sequences);
        }
    }

    @Test
    void boundedCapacityAppliesBackpressureUntilOrderedResultsAreDrained()
            throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        try (OrderedBatchExecutor executor = new OrderedBatchExecutor(1, 2, (raw, token) -> {
            gate.await();
            return processed(raw.sequence());
        })) {
            assertTrue(executor.submit(raw(0)));
            assertTrue(executor.submit(raw(1)));
            assertFalse(executor.submit(raw(2)));

            gate.countDown();
            awaitCompleted(executor, 2);
            assertTrue(executor.pollOrdered().isPresent());
            assertTrue(executor.submit(raw(2)));
        }
    }

    @Test
    void cancellationIsSharedWithWorkersAndStopsFurtherSubmission()
            throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        try (OrderedBatchExecutor executor = new OrderedBatchExecutor(1, 1, (raw, token) -> {
            entered.countDown();
            while (!token.isCancelled()) {
                Thread.onSpinWait();
            }
            return processed(raw.sequence());
        })) {
            assertTrue(executor.submit(raw(0)));
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            executor.cancel("test cancellation");
            assertTrue(executor.cancellationToken().isCancelled());
            assertFalse(executor.submit(raw(1)));
        }
    }

    @Test
    void taskFailurePropagatesFromTheNextOrderedResult() throws Exception {
        try (OrderedBatchExecutor executor = new OrderedBatchExecutor(1, 1,
                (raw, token) -> { throw new IllegalStateException("broken batch"); })) {
            assertTrue(executor.submit(raw(0)));
            awaitCompleted(executor, 1);
            OrderedBatchExecutor.BatchProcessingException failure = assertThrows(
                    OrderedBatchExecutor.BatchProcessingException.class,
                    executor::pollOrdered);
            assertEquals(0, failure.sequence());
            assertEquals("broken batch", failure.getCause().getMessage());
            assertTrue(executor.cancellationToken().isCancelled());
        }
    }

    @Test
    void closeShutsDownTheConfiguredWorkerPool() throws Exception {
        OrderedBatchExecutor executor = new OrderedBatchExecutor(
                3, 3, (raw, token) -> processed(raw.sequence()));
        assertEquals(3, executor.workerCount());
        executor.close();
        assertTrue(executor.awaitTermination(Duration.ofSeconds(1)));
    }

    private static RawChunkBatch raw(long sequence) {
        return new RawChunkBatch(
                sequence,
                new ChunkCoordinate((int) sequence, 0),
                List.of(),
                List.of(),
                BatchCounters.ZERO);
    }

    private static ChunkBatch processed(long sequence) {
        return new ChunkBatch(
                List.of(), List.of(), BatchCounters.ZERO,
                new GeometryAdjustmentStats(0, 0, 0,
                        Map.of("sequence/" + sequence, 0L)));
    }

    private static void awaitCompleted(
            OrderedBatchExecutor executor, int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (executor.completedCount() < count && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertTrue(executor.completedCount() >= count, "workers did not complete in time");
    }
}
