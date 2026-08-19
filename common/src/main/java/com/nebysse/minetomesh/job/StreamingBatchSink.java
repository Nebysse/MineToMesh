package com.nebysse.minetomesh.job;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nebysse.minetomesh.capture.RenderTypeDescriptor;
import com.nebysse.minetomesh.gltf.InternalGltfValidator;
import com.nebysse.minetomesh.material.MaterialSidecarWriter;
import com.nebysse.minetomesh.output.ExportName;
import com.nebysse.minetomesh.output.OutputTransaction;
import com.nebysse.minetomesh.output.StreamingSceneSession;
import com.nebysse.minetomesh.report.ExportReport;
import com.nebysse.minetomesh.report.ReportWriter;
import com.nebysse.minetomesh.scene.BatchCounters;
import com.nebysse.minetomesh.scene.ChunkBatch;
import com.nebysse.minetomesh.scene.Diagnostic;
import com.nebysse.minetomesh.scene.GeometryAdjustmentStats;
import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.PrimitiveMode;
import com.nebysse.minetomesh.texture.TextureImage;
import com.nebysse.minetomesh.texture.TextureRegistry;
import com.nebysse.minetomesh.world.ExportPlan;
import com.nebysse.minetomesh.world.Selection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

public final class StreamingBatchSink implements ExportJob.BatchSink {
    private static final int WRITER_QUEUE_CAPACITY = 2;

    private final ArrayBlockingQueue<Envelope> queue =
            new ArrayBlockingQueue<>(WRITER_QUEUE_CAPACITY);
    private final AtomicReference<ExportJob.WriterResult> result = new AtomicReference<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final LongSupplier gameTime;
    private final ExportTelemetry telemetry;
    private boolean terminalSent;

    public StreamingBatchSink(
            OutputTransaction transaction,
            TextureRegistry textures,
            ExportName name,
            ExportPlan plan,
            Map<String, Object> rootExtras,
            long startGameTime,
            LongSupplier gameTime,
            ExportTelemetry telemetry) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(textures, "textures");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(rootExtras, "rootExtras");
        this.gameTime = Objects.requireNonNull(gameTime, "gameTime");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        Thread writer = new Thread(
                () -> writeLoop(transaction, textures, name, plan,
                        rootExtras, startGameTime),
                "minetomesh-writer-" + name.value());
        writer.setDaemon(true);
        writer.start();
    }

    @Override
    public boolean offer(ChunkBatch batch) {
        return !terminalSent && !cancelled.get() && queue.offer(Envelope.batch(batch));
    }

    @Override
    public int queueDepth() {
        return queue.size();
    }

    @Override
    public boolean finishInput() {
        if (terminalSent) {
            return true;
        }
        if (!queue.offer(Envelope.finish(gameTime.getAsLong()))) {
            return false;
        }
        terminalSent = true;
        return true;
    }

    @Override
    public Optional<ExportJob.WriterResult> pollResult() {
        return Optional.ofNullable(result.getAndSet(null));
    }

    @Override
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            queue.clear();
            queue.offer(Envelope.cancelledMarker());
        }
    }

    private void writeLoop(
            OutputTransaction transaction,
            TextureRegistry textures,
            ExportName name,
            ExportPlan plan,
            Map<String, Object> rootExtras,
            long startGameTime) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        BatchCounters counters = BatchCounters.ZERO;
        GeometryAdjustmentStats adjustments = GeometryAdjustmentStats.ZERO;
        Set<MaterialKey> materials = new LinkedHashSet<>();
        long endGameTime = startGameTime;
        telemetry.writerStage(ExportTelemetry.WriterStage.DRAINING);
        try (transaction;
             StreamingSceneSession session = new StreamingSceneSession(
                     transaction.temporaryDirectory(), name.value(), rootExtras)) {
            while (true) {
                Envelope envelope = queue.take();
                if (envelope.cancel() || cancelled.get()) {
                    return;
                }
                if (envelope.finish()) {
                    endGameTime = envelope.gameTime();
                    break;
                }
                ChunkBatch batch = envelope.batch();
                session.append(batch);
                diagnostics.addAll(batch.diagnostics());
                counters = counters.plus(batch.counters());
                adjustments = adjustments.plus(batch.adjustments());
                batch.nodes().forEach(node -> node.primitives()
                        .forEach(primitive -> materials.add(primitive.material())));
            }

            telemetry.writerStage(ExportTelemetry.WriterStage.TEXTURES);
            textures.writeAll(transaction.temporaryDirectory());
            telemetry.writerStage(ExportTelemetry.WriterStage.DOCUMENTS);
            StreamingSceneSession.OutputStatistics output = session.finish();
            writeMaterialSidecars(transaction.temporaryDirectory(), materials, textures);
            List<String> validationErrors = validate(output.gltf().gltfPath());
            for (String error : validationErrors) {
                diagnostics.add(new Diagnostic(
                        Diagnostic.Severity.FATAL,
                        "INTERNAL_GLTF_VALIDATION_FAILED",
                        name.value(),
                        Optional.empty(),
                        "",
                        "",
                        error));
            }
            BatchCounters finalCounters = withAssetCounts(
                    counters, materials.size(), textures.size());
            boolean fatal = diagnostics.stream()
                    .anyMatch(value -> value.severity() == Diagnostic.Severity.FATAL);
            long warnings = warningCount(diagnostics);
            String status = fatal ? "failed"
                    : warnings == 0 ? "completed" : "completed_with_warnings";
            telemetry.writerStage(ExportTelemetry.WriterStage.REPORT);
            ReportWriter.write(transaction.temporaryDirectory(), report(
                    status, plan, startGameTime, endGameTime,
                    finalCounters, adjustments, diagnostics));
            if (fatal) {
                result.set(ExportJob.WriterResult.failure(
                        "Internal glTF validation failed with "
                                + validationErrors.size() + " error(s)"));
                return;
            }
            Path published = transaction.publish();
            telemetry.writerStage(ExportTelemetry.WriterStage.COMMITTED);
            result.set(ExportJob.WriterResult.success(
                    published,
                    warnings,
                    status,
                    output.gltf().nodeCount(),
                    output.gltf().primitiveCount(),
                    textures.size()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (!cancelled.get()) {
                result.set(ExportJob.WriterResult.failure("Writer thread was interrupted"));
            }
        } catch (Exception exception) {
            if (!cancelled.get()) {
                result.set(ExportJob.WriterResult.failure(
                        exception.getMessage() == null
                                ? exception.getClass().getSimpleName() : exception.getMessage()));
            }
            try {
                transaction.close();
            } catch (IOException ignored) {
                // The primary writer error is retained.
            }
        }
    }

    private static long warningCount(List<Diagnostic> diagnostics) {
        return diagnostics.stream()
                .filter(value -> value.severity() == Diagnostic.Severity.WARNING
                        || value.severity() == Diagnostic.Severity.FAILURE)
                .count();
    }

    private static void writeMaterialSidecars(
            Path root,
            Set<MaterialKey> materials,
            TextureRegistry textures) throws IOException {
        Map<com.nebysse.minetomesh.scene.TextureKey, TextureImage.AnimationInfo> animations =
                new LinkedHashMap<>();
        for (TextureRegistry.Entry entry : textures.entries()) {
            entry.image().animation().ifPresent(value -> animations.put(entry.key(), value));
        }
        int index = 0;
        for (MaterialKey material : materials) {
            RenderTypeDescriptor descriptor = new RenderTypeDescriptor(
                    "captured",
                    PrimitiveMode.QUADS,
                    Optional.of(material.texture().sourceId()),
                    material.alphaMode(),
                    material.alphaCutoff(),
                    !material.doubleSided(),
                    material.emissive(),
                    material.blendSemantic(),
                    material.samplerMode() == MaterialKey.SamplerMode.NEAREST_MIPMAP,
                    false);
            MaterialSidecarWriter.write(root, new MaterialSidecarWriter.MaterialRecord(
                    index++,
                    material,
                    descriptor,
                    Optional.ofNullable(animations.get(material.texture())),
                    List.of()));
        }
    }

    private static List<String> validate(Path gltfPath) throws IOException {
        JsonObject document = JsonParser.parseString(
                Files.readString(gltfPath, StandardCharsets.UTF_8)).getAsJsonObject();
        return InternalGltfValidator.validate(document, gltfPath.getParent());
    }

    private static BatchCounters withAssetCounts(
            BatchCounters source,
            long materials,
            long textures) {
        return new BatchCounters(
                source.scannedPositions(),
                source.renderedBlocks(),
                source.renderedFluids(),
                source.blockEntities(),
                source.entities(),
                materials,
                textures,
                source.triangles(),
                source.placeholders());
    }

    private static ExportReport report(
            String status,
            ExportPlan plan,
            long startGameTime,
            long endGameTime,
            BatchCounters counters,
            GeometryAdjustmentStats adjustments,
            List<Diagnostic> diagnostics) {
        Selection selection = plan.selection();
        return new ExportReport(
                status,
                "rolling_client_snapshot",
                selection.min().dimension(),
                new int[] {selection.min().x(), selection.min().y(), selection.min().z()},
                new int[] {selection.max().x(), selection.max().y(), selection.max().z()},
                new int[] {selection.min().x(), selection.min().y(), selection.min().z()},
                selection.volume(),
                startGameTime,
                endGameTime,
                counters,
                adjustments,
                plan.missingChunks().stream()
                        .map(chunk -> new ExportReport.MissingChunk(
                                chunk.chunkX(), chunk.chunkZ()))
                        .toList(),
                diagnostics,
                Map.of());
    }

    private record Envelope(
            ChunkBatch batch,
            boolean finish,
            boolean cancel,
            long gameTime) {
        private static Envelope batch(ChunkBatch batch) {
            return new Envelope(Objects.requireNonNull(batch, "batch"), false, false, 0);
        }

        private static Envelope finish(long gameTime) {
            return new Envelope(null, true, false, gameTime);
        }

        private static Envelope cancelledMarker() {
            return new Envelope(null, false, true, 0);
        }
    }
}
