package com.nebysse.minetomesh.job;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nebysse.minetomesh.MineToMesh;
import com.nebysse.minetomesh.capture.BlockEntityCapture;
import com.nebysse.minetomesh.capture.BlockModelExtractor;
import com.nebysse.minetomesh.capture.BlockPrimitiveRouter;
import com.nebysse.minetomesh.capture.CaptureCoordinates;
import com.nebysse.minetomesh.capture.CaptureState;
import com.nebysse.minetomesh.capture.EntityCapture;
import com.nebysse.minetomesh.capture.FluidGeometryCapture;
import com.nebysse.minetomesh.capture.ObjectCaptureDecision;
import com.nebysse.minetomesh.capture.PlaceholderFactory;
import com.nebysse.minetomesh.capture.RenderTypeDescriptor;
import com.nebysse.minetomesh.gltf.InternalGltfValidator;
import com.nebysse.minetomesh.material.MaterialResolver;
import com.nebysse.minetomesh.material.MaterialSidecarWriter;
import com.nebysse.minetomesh.output.ExportName;
import com.nebysse.minetomesh.output.OutputTransaction;
import com.nebysse.minetomesh.output.StreamingSceneSession;
import com.nebysse.minetomesh.report.ExportReport;
import com.nebysse.minetomesh.report.ReportWriter;
import com.nebysse.minetomesh.scene.BatchCounters;
import com.nebysse.minetomesh.scene.CapturedNode;
import com.nebysse.minetomesh.scene.ChunkBatch;
import com.nebysse.minetomesh.scene.Diagnostic;
import com.nebysse.minetomesh.scene.GeometryAdjustmentStats;
import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.PrimitiveAccumulator;
import com.nebysse.minetomesh.scene.PrimitiveMode;
import com.nebysse.minetomesh.texture.AtlasSpriteResolver;
import com.nebysse.minetomesh.texture.GlGpuTextureAccess;
import com.nebysse.minetomesh.texture.GpuTextureProvider;
import com.nebysse.minetomesh.texture.ResourceTextureExtractor;
import com.nebysse.minetomesh.texture.SpriteTextureExtractor;
import com.nebysse.minetomesh.texture.TextureAcquisitionChain;
import com.nebysse.minetomesh.texture.TextureImage;
import com.nebysse.minetomesh.texture.TextureProvider;
import com.nebysse.minetomesh.texture.TextureRegistry;
import com.nebysse.minetomesh.world.ExportPlan;
import com.nebysse.minetomesh.world.Selection;
import com.nebysse.minetomesh.world.WorldPlanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

public final class DefaultExportPipeline {
    private static final int WRITER_QUEUE_CAPACITY = 2;

    private DefaultExportPipeline() {
    }

    static boolean shouldCreateBlockPlaceholder(
            CaptureState staticState,
            CaptureState auxiliaryState) {
        Objects.requireNonNull(staticState, "staticState");
        return ObjectCaptureDecision.decide(
                staticState == CaptureState.GEOMETRY,
                auxiliaryState).placeholder();
    }

    static long warningCount(List<Diagnostic> diagnostics) {
        return diagnostics.stream()
                .filter(value -> value.severity() == Diagnostic.Severity.WARNING
                        || value.severity() == Diagnostic.Severity.FAILURE)
                .count();
    }

    public static ExportJob create(
            Minecraft minecraft,
            Selection selection,
            ExportName name) throws IOException {
        return create(minecraft, selection, name, ExportOptions.DEFAULT, new ExportTelemetry());
    }

    public static ExportJob create(
            Minecraft minecraft,
            Selection selection,
            ExportName name,
            ExportTelemetry telemetry) throws IOException {
        return create(minecraft, selection, name, ExportOptions.DEFAULT, telemetry);
    }

    public static ExportJob create(
            Minecraft minecraft,
            Selection selection,
            ExportName name,
            ExportOptions options,
            ExportTelemetry telemetry) throws IOException {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(telemetry, "telemetry");
        ClientLevel level = Objects.requireNonNull(minecraft.level, "No active client world");
        ExportPlan plan = new WorldPlanner().plan(level, selection);
        Path exportRoot = exportRoot(minecraft.gameDirectory.toPath());
        OutputTransaction transaction = OutputTransaction.begin(exportRoot, name);
        try {
            TextureRegistry textures = new TextureRegistry();
            textures.register(PlaceholderFactory.TEXTURE, PlaceholderFactory.textureImage());
            ProductionCaptureSource source = new ProductionCaptureSource(
                    minecraft, level, plan, textures, options);
            AsyncBatchSink sink = new AsyncBatchSink(
                    transaction,
                    textures,
                    name,
                    plan,
                    rootExtras(minecraft, plan, options),
                    level.getGameTime(),
                    telemetry);
            return new ExportJob(
                    source, sink, System::nanoTime, Duration.ofMillis(6), telemetry);
        } catch (RuntimeException | Error exception) {
            transaction.close();
            throw exception;
        }
    }

    static Path exportRoot(Path gameDirectory) {
        return Objects.requireNonNull(gameDirectory, "gameDirectory")
                .resolve("minetomesh-exports");
    }

    private static Map<String, Object> rootExtras(
            Minecraft minecraft, ExportPlan plan, ExportOptions options) {
        Selection selection = plan.selection();
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("minecraftVersion", "1.21.1");
        extras.put("neoForgeVersion", loadedModVersion("neoforge"));
        extras.put("exporterVersion", MineToMesh.VERSION);
        extras.put("dimension", selection.min().dimension());
        extras.put("selectionMin", List.of(
                selection.min().x(), selection.min().y(), selection.min().z()));
        extras.put("selectionMax", List.of(
                selection.max().x(), selection.max().y(), selection.max().z()));
        extras.put("origin", List.of(
                selection.min().x(), selection.min().y(), selection.min().z()));
        extras.put("activeResourcePacks", List.copyOf(
                minecraft.getResourcePackRepository().getSelectedIds()));
        extras.put("loadedMods", ModList.get().getMods().stream()
                .map(info -> info.getModId() + "@" + info.getVersion())
                .sorted()
                .toList());
        extras.put("snapshotMode", "rolling_client_snapshot");
        extras.put("formats", List.of("gltf", "obj"));
        extras.put("includePlayers", options.includePlayers());
        extras.put("sourceTopologyPreservedInObj", true);
        return extras;
    }

    private static String loadedModVersion(String modId) {
        return ModList.get().getMods().stream()
                .filter(info -> info.getModId().equals(modId))
                .map(info -> info.getVersion().toString())
                .findFirst()
                .orElse("unknown");
    }

    private static final class ProductionCaptureSource implements ExportJob.CaptureSource {
        private final ClientLevel level;
        private final ExportPlan plan;
        private final TextureRegistry textures;
        private final ExportOptions options;
        private final BlockModelExtractor blocks;
        private final FluidGeometryCapture fluids;
        private final BlockEntityCapture blockEntities;
        private final EntityCapture entities;
        private final List<Diagnostic> materialDiagnostics = new ArrayList<>();

        private ProductionCaptureSource(
                Minecraft minecraft,
                ClientLevel level,
                ExportPlan plan,
                TextureRegistry textures,
                ExportOptions options) {
            this.level = level;
            this.plan = plan;
            this.textures = textures;
            this.options = options;
            SpriteTextureExtractor sprites = new SpriteTextureExtractor(minecraft.getResourceManager());
            AtlasSpriteResolver atlasSprites = new AtlasSpriteResolver(
                    atlasId -> minecraft.getModelManager().getAtlas(atlasId));
            this.blocks = new BlockModelExtractor(sprites, atlasSprites, textures);
            this.fluids = new FluidGeometryCapture(sprites, textures);
            Function<RenderTypeDescriptor, MaterialKey> materialResolver =
                    resourceMaterialResolver(minecraft, textures, materialDiagnostics);
            this.blockEntities = new BlockEntityCapture(materialResolver);
            this.entities = new EntityCapture(materialResolver);
        }

        @Override
        public ChunkBatch captureEntities() {
            EntityCapture.CaptureResult captured = entities.captureAll(
                    level, plan.selection(), options.includePlayers());
            List<Diagnostic> diagnostics = new ArrayList<>(captured.diagnostics());
            diagnostics.addAll(drainMaterialDiagnostics());
            return new ChunkBatch(captured.nodes(), diagnostics, captured.counters());
        }

        @Override
        public int sectionCount() {
            return plan.sections().size();
        }

        @Override
        public ExportJob.SectionCapture openSection(int index) {
            return new SectionCursor(plan.sections().get(index));
        }

        private List<Diagnostic> drainMaterialDiagnostics() {
            List<Diagnostic> drained = List.copyOf(materialDiagnostics);
            materialDiagnostics.clear();
            return drained;
        }

        private final class SectionCursor implements ExportJob.SectionCapture {
            private final ExportPlan.SectionWork work;
            private final PrimitiveAccumulator accumulator;
            private final PrimitiveAccumulator overlayAccumulator;
            private final List<CapturedNode> nodes = new ArrayList<>();
            private final List<Diagnostic> diagnostics = new ArrayList<>();
            private BatchCounters counters = BatchCounters.ZERO;
            private GeometryAdjustmentStats adjustments = GeometryAdjustmentStats.ZERO;
            private long next;

            private SectionCursor(ExportPlan.SectionWork work) {
                this.work = work;
                this.accumulator = new PrimitiveAccumulator(objectId());
                this.overlayAccumulator = new PrimitiveAccumulator(
                        BlockPrimitiveRouter.OVERLAY_OBJECT_NAME);
            }

            @Override
            public String objectId() {
                return "chunk/" + work.section().chunkX() + "/"
                        + work.section().chunkZ() + "/section/" + work.section().sectionY();
            }

            @Override
            public boolean hasNext() {
                return next < work.positionCount();
            }

            @Override
            public void captureNext() {
                BlockPos position = position(next++);
                counters = counters.plus(new BatchCounters(1, 0, 0, 0, 0, 0, 0, 0, 0));

                BlockModelExtractor.CaptureResult block = blocks.capture(
                        level, position, plan.selection(), accumulator, overlayAccumulator);
                counters = counters.plus(block.counters());
                adjustments = adjustments.plus(block.adjustments());
                diagnostics.addAll(block.diagnostics());

                FluidGeometryCapture.CaptureResult fluid = fluids.capture(
                        level, position, plan.selection(), accumulator);
                counters = counters.plus(fluid.counters());
                diagnostics.addAll(fluid.diagnostics());

                BlockEntity blockEntity = level.getBlockEntity(position);
                if (blockEntity != null) {
                    BlockEntityCapture.CaptureResult rendered = blockEntities.capture(
                            blockEntity, plan.selection());
                    rendered.node().ifPresent(nodes::add);
                    counters = counters.plus(rendered.counters());
                    diagnostics.addAll(rendered.diagnostics());
                    ObjectCaptureDecision decision = ObjectCaptureDecision.decide(
                            block.hasGeometry(), rendered.state());
                    if (decision.placeholder()) {
                        nodes.add(blockPlaceholder(position));
                        counters = counters.plus(placeholderCounter());
                    } else if (decision.partial()) {
                        diagnostics.add(new Diagnostic(
                                Diagnostic.Severity.WARNING,
                                "PARTIAL_OBJECT_CAPTURE",
                                "block/" + position.toShortString(),
                                Optional.empty(),
                                "",
                                "",
                                "Static block geometry was kept after auxiliary capture failed"));
                    }
                } else if (block.state() == CaptureState.FAILED) {
                    nodes.add(blockPlaceholder(position));
                    counters = counters.plus(placeholderCounter());
                }
            }

            @Override
            public ChunkBatch finish() {
                PrimitiveAccumulator.SealResult sealed = accumulator.seal();
                PrimitiveAccumulator.SealResult overlaySealed = overlayAccumulator.seal();
                diagnostics.addAll(sealed.diagnostics());
                diagnostics.addAll(overlaySealed.diagnostics());
                diagnostics.addAll(drainMaterialDiagnostics());
                if (!sealed.primitives().isEmpty()) {
                    nodes.addFirst(new CapturedNode(
                            objectId(),
                            CapturedNode.Kind.CHUNK,
                            sealed.primitives(),
                            Map.of(
                                    "chunkX", work.section().chunkX(),
                                    "chunkZ", work.section().chunkZ(),
                                    "sectionY", work.section().sectionY())));
                }
                if (!overlaySealed.primitives().isEmpty()) {
                    nodes.add(new CapturedNode(
                            BlockPrimitiveRouter.OVERLAY_OBJECT_NAME,
                            CapturedNode.Kind.OVERLAY,
                            overlaySealed.primitives(),
                            Map.of(
                                    "layerRole", "grass_side_overlay",
                                    "scope", "selection",
                                    "sourceTexture", BlockPrimitiveRouter.GRASS_SIDE_OVERLAY_ID)));
                }
                return new ChunkBatch(nodes, diagnostics, counters, adjustments);
            }

            private BlockPos position(long index) {
                int width = work.maxX() - work.minX() + 1;
                int depth = work.maxZ() - work.minZ() + 1;
                int x = (int) (index % width);
                long remaining = index / width;
                int z = (int) (remaining % depth);
                int y = (int) (remaining / depth);
                return new BlockPos(work.minX() + x, work.minY() + y, work.minZ() + z);
            }

            private static BatchCounters placeholderCounter() {
                return new BatchCounters(0, 0, 0, 0, 0, 0, 0, 12, 1);
            }

            private CapturedNode blockPlaceholder(BlockPos position) {
                CaptureCoordinates.Bounds bounds = CaptureCoordinates.blockBounds(
                        position, plan.selection());
                return PlaceholderFactory.create(
                        "block/" + position.toShortString(),
                        bounds.min(),
                        bounds.max(),
                        Map.of(
                                "worldPosition", List.of(
                                        position.getX(), position.getY(), position.getZ()),
                                "fallbackReason", "BLOCK_MODEL_CAPTURE_FAILED"));
            }
        }
    }

    private static Function<RenderTypeDescriptor, MaterialKey> resourceMaterialResolver(
            Minecraft minecraft,
            TextureRegistry textures,
            List<Diagnostic> diagnostics) {
        TextureAcquisitionChain chain = new TextureAcquisitionChain(
                List.of(
                        ResourceTextureExtractor.resourceProvider(),
                        ResourceTextureExtractor.dynamicProvider(),
                        new GpuTextureProvider(new GlGpuTextureAccess())),
                TextureAcquisitionChain::missing);
        Map<String, TextureProvider.Result> cache = new LinkedHashMap<>();
        return descriptor -> {
            String resourceId = descriptor.textureResourceId()
                    .orElse("minetomesh:missing_texture");
            TextureProvider.Result extraction = cache.get(resourceId);
            if (extraction == null) {
                ResourceLocation id = ResourceLocation.parse(resourceId);
                extraction = chain.acquire(new TextureProvider.Request(
                        id,
                        minecraft.getResourceManager(),
                        minecraft.getTextureManager()));
                cache.put(resourceId, extraction);
                textures.register(extraction.key(), extraction.image());
                diagnostics.addAll(extraction.diagnostics());
            }
            return MaterialResolver.resolve(descriptor, extraction.key());
        };
    }

    private static final class AsyncBatchSink implements ExportJob.BatchSink {
        private final ArrayBlockingQueue<Envelope> queue =
                new ArrayBlockingQueue<>(WRITER_QUEUE_CAPACITY);
        private final AtomicReference<ExportJob.WriterResult> result = new AtomicReference<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final ClientLevel level;
        private final ExportTelemetry telemetry;
        private boolean terminalSent;

        private AsyncBatchSink(
                OutputTransaction transaction,
                TextureRegistry textures,
                ExportName name,
                ExportPlan plan,
                Map<String, Object> rootExtras,
                long startGameTime,
                ExportTelemetry telemetry) {
            this.level = Objects.requireNonNull(Minecraft.getInstance().level, "level");
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
            if (!queue.offer(Envelope.finish(level.getGameTime()))) {
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
                            "Internal glTF validation failed with " + validationErrors.size() + " error(s)"));
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
                        .map(chunk -> new ExportReport.MissingChunk(chunk.chunkX(), chunk.chunkZ()))
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
