package com.nebysse.minetomesh.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nebysse.minetomesh.scene.BatchCounters;
import com.nebysse.minetomesh.scene.ChunkBatch;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class ExportJobTest {
    @Test
    void capturesEntitiesBeforeStableSectionsThenWaitsForWriterCompletion() {
        List<String> events = new ArrayList<>();
        FakeSource source = new FakeSource(events, List.of("s0", "s1"), 1);
        FakeSink sink = new FakeSink(events, 2);
        ExportJob job = new ExportJob(source, sink, new StepClock(0), Duration.ofMillis(6));

        while (job.state() == JobState.CAPTURING) {
            job.tick();
            sink.drainOne();
        }

        assertEquals(List.of(
                "capture:entities", "write:entities",
                "capture:s0:0", "finish:s0", "write:s0",
                "capture:s1:0", "finish:s1", "write:s1",
                "finish-input"), events);
        assertEquals(JobState.WRITING, job.state());
        sink.complete(ExportJob.WriterResult.success(
                Path.of("export"), 0, "completed"));
        job.tick();
        assertEquals(JobState.COMPLETED, job.state());
    }

    @Test
    void sixMillisecondBudgetResumesRemainingSectionOnNextTick() {
        List<String> events = new ArrayList<>();
        FakeSource source = new FakeSource(events, List.of("s0"), 8);
        FakeSink sink = new FakeSink(events, 2);
        ExportJob job = new ExportJob(source, sink,
                new StepClock(Duration.ofMillis(1).toNanos()), Duration.ofMillis(6));

        job.tick();
        int firstTickPositions = source.capturedPositions;
        assertTrue(firstTickPositions > 0 && firstTickPositions < 8);
        sink.drainOne();
        job.tick();
        assertTrue(source.capturedPositions > firstTickPositions);
    }

    @Test
    void queueDepthTwoPausesCaptureUntilWriterMakesSpace() {
        List<String> events = new ArrayList<>();
        FakeSource source = new FakeSource(events, List.of("s0", "s1", "s2"), 1);
        FakeSink sink = new FakeSink(events, 2);
        ExportJob job = new ExportJob(source, sink, new StepClock(0), Duration.ofMillis(6));

        job.tick();
        assertEquals(2, sink.queueDepth());
        int capturedBeforePause = source.capturedPositions;
        job.tick();
        assertEquals(capturedBeforePause, source.capturedPositions);
        sink.drainOne();
        job.tick();
        assertTrue(source.capturedPositions > capturedBeforePause);
    }

    @Test
    void preservesCompletedWithWarningsOutcomeFromWriter() {
        FakeSink sink = new FakeSink(new ArrayList<>(), 2);
        ExportJob job = new ExportJob(
                new FakeSource(new ArrayList<>(), List.of(), 0),
                sink, new StepClock(0), Duration.ofMillis(6));
        job.tick();
        sink.complete(ExportJob.WriterResult.success(
                Path.of("warning-export"), 3, "completed_with_warnings"));
        job.tick();

        assertEquals(JobState.COMPLETED, job.state());
        assertEquals("completed_with_warnings", job.outcomeStatus());
        assertEquals(3, job.warningCount());
    }

    @Test
    void cancellationAndWriterFailureBecomeTerminal() {
        FakeSink cancelledSink = new FakeSink(new ArrayList<>(), 2);
        ExportJob cancelled = new ExportJob(
                new FakeSource(new ArrayList<>(), List.of("s0"), 4),
                cancelledSink, new StepClock(0), Duration.ofMillis(6));
        cancelled.cancel("user");
        assertEquals(JobState.CANCELLED, cancelled.state());
        assertTrue(cancelledSink.cancelled);

        FakeSink failedSink = new FakeSink(new ArrayList<>(), 2);
        ExportJob failed = new ExportJob(
                new FakeSource(new ArrayList<>(), List.of(), 0),
                failedSink, new StepClock(0), Duration.ofMillis(6));
        failed.tick();
        assertEquals(JobState.WRITING, failed.state());
        failedSink.complete(ExportJob.WriterResult.failure("disk full"));
        failed.tick();
        assertEquals(JobState.FAILED, failed.state());
        assertEquals("disk full", failed.failureReason().orElseThrow());
    }

    @Test
    void completedSummaryCarriesWriterCountsAndDirectory() {
        FakeSink sink = new FakeSink(new ArrayList<>(), 2);
        ExportJob job = new ExportJob(
                new FakeSource(new ArrayList<>(), List.of(), 0),
                sink, new StepClock(0), Duration.ofMillis(6));
        job.tick();
        sink.complete(ExportJob.WriterResult.success(
                Path.of("flower-export"), 2, "completed_with_warnings", 41, 512, 7));
        job.tick();

        ExportSummary summary = job.summary().orElseThrow();
        assertEquals("completed_with_warnings", summary.status());
        assertEquals(Path.of("flower-export"), summary.outputDirectory().orElseThrow());
        assertEquals(41, summary.nodeCount());
        assertEquals(512, summary.primitiveCount());
        assertEquals(7, summary.textureCount());
        assertEquals(2, summary.warningCount());
        assertTrue(summary.failureReason().isEmpty());
    }

    @Test
    void cancelledSummaryCarriesReasonWithoutDirectory() {
        FakeSink sink = new FakeSink(new ArrayList<>(), 2);
        ExportJob job = new ExportJob(
                new FakeSource(new ArrayList<>(), List.of("s0"), 4),
                sink, new StepClock(0), Duration.ofMillis(6));
        job.cancel("screen_closed");

        ExportSummary summary = job.summary().orElseThrow();
        assertEquals("cancelled", summary.status());
        assertTrue(summary.outputDirectory().isEmpty());
        assertEquals("screen_closed", summary.failureReason().orElseThrow());
    }

    private static ChunkBatch batch(String ignored) {
        return new ChunkBatch(List.of(), List.of(), BatchCounters.ZERO);
    }

    private static final class FakeSource implements ExportJob.CaptureSource {
        private final List<String> events;
        private final List<String> sections;
        private final int positionsPerSection;
        private int capturedPositions;

        private FakeSource(List<String> events, List<String> sections, int positionsPerSection) {
            this.events = events;
            this.sections = sections;
            this.positionsPerSection = positionsPerSection;
        }

        @Override
        public ChunkBatch captureEntities() {
            events.add("capture:entities");
            return batch("entities");
        }

        @Override
        public int sectionCount() {
            return sections.size();
        }

        @Override
        public ExportJob.SectionCapture openSection(int index) {
            String id = sections.get(index);
            return new ExportJob.SectionCapture() {
                private int position;

                @Override
                public String objectId() {
                    return id;
                }

                @Override
                public boolean hasNext() {
                    return position < positionsPerSection;
                }

                @Override
                public void captureNext() {
                    events.add("capture:" + id + ":" + position++);
                    capturedPositions++;
                }

                @Override
                public ChunkBatch finish() {
                    events.add("finish:" + id);
                    return batch(id);
                }
            };
        }
    }

    private static final class FakeSink implements ExportJob.BatchSink {
        private final List<String> events;
        private final int capacity;
        private final Queue<ChunkBatch> queue = new ArrayDeque<>();
        private Optional<ExportJob.WriterResult> result = Optional.empty();
        private int writes;
        private boolean cancelled;

        private FakeSink(List<String> events, int capacity) {
            this.events = events;
            this.capacity = capacity;
        }

        @Override
        public boolean offer(ChunkBatch batch) {
            if (queue.size() == capacity) {
                return false;
            }
            queue.add(batch);
            String name = switch (writes++) {
                case 0 -> "entities";
                default -> "s" + (writes - 2);
            };
            events.add("write:" + name);
            return true;
        }

        @Override
        public int queueDepth() {
            return queue.size();
        }

        @Override
        public boolean finishInput() {
            events.add("finish-input");
            return true;
        }

        @Override
        public Optional<ExportJob.WriterResult> pollResult() {
            Optional<ExportJob.WriterResult> current = result;
            result = Optional.empty();
            return current;
        }

        @Override
        public void cancel() {
            cancelled = true;
            queue.clear();
        }

        void drainOne() {
            queue.poll();
        }

        void complete(ExportJob.WriterResult completed) {
            result = Optional.of(completed);
        }
    }

    private static final class StepClock implements LongSupplier {
        private final long step;
        private long now;

        private StepClock(long step) {
            this.step = step;
        }

        @Override
        public long getAsLong() {
            long value = now;
            now += step;
            return value;
        }
    }
}
