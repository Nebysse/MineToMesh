package com.nebysse.minetomesh.job;

import com.nebysse.minetomesh.capture.CoplanarQuadLayering;
import com.nebysse.minetomesh.scene.CapturedNode;
import com.nebysse.minetomesh.scene.ChunkBatch;
import com.nebysse.minetomesh.scene.Diagnostic;
import com.nebysse.minetomesh.scene.GeometryAdjustmentStats;
import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.PrimitiveAccumulator;
import com.nebysse.minetomesh.scene.PrimitiveMode;
import com.nebysse.minetomesh.scene.Vertex;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class OrderedBatchExecutor implements AutoCloseable {
    @FunctionalInterface
    public interface BatchProcessor {
        ChunkBatch process(RawChunkBatch raw, CancellationToken token) throws Exception;
    }

    public record CompletedBatch(long sequence, ChunkBatch batch) {
        public CompletedBatch {
            if (sequence < 0) {
                throw new IllegalArgumentException("Batch sequence must not be negative");
            }
            Objects.requireNonNull(batch, "batch");
        }
    }

    public static final class BatchProcessingException extends RuntimeException {
        private final long sequence;

        BatchProcessingException(long sequence, Throwable cause) {
            super("Failed to process batch " + sequence, cause);
            this.sequence = sequence;
        }

        public long sequence() {
            return sequence;
        }
    }

    private final int workerCount;
    private final BatchProcessor processor;
    private final ExecutorService workers;
    private final Semaphore capacity;
    private final CancellationToken cancellationToken = new CancellationToken();
    private final ConcurrentSkipListMap<Long, Outcome> completed =
            new ConcurrentSkipListMap<>();
    private final Set<Long> submitted = ConcurrentHashMap.newKeySet();
    private final AtomicInteger completedCount = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();
    private long nextSequence;

    public OrderedBatchExecutor(
            int workerCount, int capacity, BatchProcessor processor) {
        if (workerCount < 1) {
            throw new IllegalArgumentException("Worker count must be positive");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.workerCount = workerCount;
        this.processor = Objects.requireNonNull(processor, "processor");
        this.capacity = new Semaphore(capacity);
        AtomicInteger threadSequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(
                    runnable,
                    "minetomesh-worker-" + threadSequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.workers = Executors.newFixedThreadPool(workerCount, factory);
    }

    public boolean submit(RawChunkBatch raw) {
        Objects.requireNonNull(raw, "raw");
        if (closed.get() || cancellationToken.isCancelled() || !capacity.tryAcquire()) {
            return false;
        }
        if (raw.sequence() < nextSequence || !submitted.add(raw.sequence())) {
            capacity.release();
            return false;
        }
        try {
            workers.execute(() -> process(raw));
            return true;
        } catch (RejectedExecutionException exception) {
            submitted.remove(raw.sequence());
            capacity.release();
            return false;
        }
    }

    public synchronized Optional<CompletedBatch> pollOrdered() {
        Outcome outcome = completed.remove(nextSequence);
        if (outcome == null) {
            return Optional.empty();
        }
        long sequence = nextSequence++;
        submitted.remove(sequence);
        capacity.release();
        if (outcome.failure != null) {
            throw new BatchProcessingException(sequence, outcome.failure);
        }
        return Optional.of(new CompletedBatch(sequence, outcome.batch));
    }

    public void cancel(String reason) {
        Objects.requireNonNull(reason, "reason");
        if (cancellationToken.cancel(reason)) {
            closed.set(true);
            workers.shutdownNow();
        }
    }

    public CancellationToken cancellationToken() {
        return cancellationToken;
    }

    public int workerCount() {
        return workerCount;
    }

    public int completedCount() {
        return completedCount.get();
    }

    public int inFlightCount() {
        return submitted.size();
    }

    public void finishSubmissions() {
        if (closed.compareAndSet(false, true)) {
            workers.shutdown();
        }
    }

    public boolean awaitTermination(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must not be negative");
        }
        return workers.awaitTermination(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            cancellationToken.cancel("executor closed");
            workers.shutdownNow();
        } else if (!workers.isShutdown()) {
            workers.shutdownNow();
        }
    }

    public static ChunkBatch processRaw(
            RawChunkBatch raw, CancellationToken token) {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(token, "token");
        List<CapturedNode> nodes = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>(raw.diagnostics());
        GeometryAdjustmentStats adjustments = GeometryAdjustmentStats.ZERO;
        for (RawCapturedObject object : raw.objects()) {
            throwIfCancelled(token);
            PrimitiveAccumulator accumulator = new PrimitiveAccumulator(object.objectId());
            Map<String, List<LayeredQuad>> quadGroups = new LinkedHashMap<>();
            for (RawPrimitiveStream stream : object.streams()) {
                throwIfCancelled(token);
                if (stream.mode() != PrimitiveMode.QUADS) {
                    accumulator.append(stream.material(), stream.mode(), stream.vertices());
                    continue;
                }
                int completeVertices = stream.vertices().size()
                        - stream.vertices().size() % 4;
                List<LayeredQuad> group = quadGroups.computeIfAbsent(
                        stream.layerGroupId(), ignored -> new ArrayList<>());
                for (int index = 0; index < completeVertices; index += 4) {
                    group.add(new LayeredQuad(
                            stream.material(),
                            stream.vertices().subList(index, index + 4)));
                }
                if (completeVertices < stream.vertices().size()) {
                    accumulator.append(
                            stream.material(),
                            stream.mode(),
                            stream.vertices().subList(
                                    completeVertices, stream.vertices().size()));
                }
            }
            for (Map.Entry<String, List<LayeredQuad>> entry : quadGroups.entrySet()) {
                throwIfCancelled(token);
                CoplanarQuadLayering.Result layered = CoplanarQuadLayering.apply(
                        entry.getValue().stream().map(LayeredQuad::vertices).toList());
                for (int index = 0; index < entry.getValue().size(); index++) {
                    accumulator.append(
                            entry.getValue().get(index).material(),
                            PrimitiveMode.QUADS,
                            layered.quads().get(index));
                }
                CoplanarQuadLayering.Statistics stats = layered.statistics();
                adjustments = adjustments.plus(GeometryAdjustmentStats.forBlock(
                        entry.getKey(),
                        stats.coplanarGroups(),
                        stats.offsetFaces(),
                        stats.maxLayers()));
            }
            PrimitiveAccumulator.SealResult sealed = accumulator.seal();
            diagnostics.addAll(sealed.diagnostics());
            nodes.add(new CapturedNode(
                    object.objectId(), object.kind(), sealed.primitives(), object.extras()));
        }
        return new ChunkBatch(nodes, diagnostics, raw.counters(), adjustments);
    }

    private void process(RawChunkBatch raw) {
        Outcome outcome;
        try {
            if (cancellationToken.isCancelled()) {
                return;
            }
            outcome = Outcome.success(processor.process(raw, cancellationToken));
        } catch (Throwable failure) {
            cancellationToken.cancel("batch " + raw.sequence() + " failed");
            outcome = Outcome.failure(failure);
        }
        completed.put(raw.sequence(), outcome);
        completedCount.incrementAndGet();
    }

    private static void throwIfCancelled(CancellationToken token) {
        if (token.isCancelled()) {
            throw new CancellationException(
                    token.reason().orElse("Export processing cancelled"));
        }
    }

    private record LayeredQuad(MaterialKey material, List<Vertex> vertices) {
        private LayeredQuad {
            Objects.requireNonNull(material, "material");
            vertices = List.copyOf(vertices);
        }
    }

    private static final class Outcome {
        private final ChunkBatch batch;
        private final Throwable failure;

        private Outcome(ChunkBatch batch, Throwable failure) {
            this.batch = batch;
            this.failure = failure;
        }

        static Outcome success(ChunkBatch batch) {
            return new Outcome(Objects.requireNonNull(batch, "batch"), null);
        }

        static Outcome failure(Throwable failure) {
            return new Outcome(null, Objects.requireNonNull(failure, "failure"));
        }
    }
}
