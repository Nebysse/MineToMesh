package com.nebysse.minetomesh.job;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nebysse.minetomesh.MineToMeshInfo;
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
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.fabricmc.loader.api.FabricLoader;

public final class DefaultExportPipeline {
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
            StreamingBatchSink sink = new StreamingBatchSink(
                    transaction,
                    textures,
                    name,
                    plan,
                    rootExtras(minecraft, plan, options),
                    level.getGameTime(),
                    level::getGameTime,
                    telemetry);
            return new ExportJob(
                    source, sink, System::nanoTime, Duration.ofMillis(6), telemetry);
        } catch (RuntimeException | Error exception) {
            transaction.close();
            throw exception;
        }
    }

    public record RollingExport(ExportJob job, RollingCaptureSource source) {
        public RollingExport {
            Objects.requireNonNull(job, "job");
            Objects.requireNonNull(source, "source");
        }
    }

    public static RollingExport createRolling(
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
        ExportPlan metadataPlan = new ExportPlan(selection, List.of(), List.of());
        Path exportRoot = exportRoot(minecraft.gameDirectory.toPath());
        OutputTransaction transaction = OutputTransaction.begin(exportRoot, name);
        try {
            TextureRegistry textures = new TextureRegistry();
            textures.register(PlaceholderFactory.TEXTURE, PlaceholderFactory.textureImage());
            RollingCaptureSource source = new RollingCaptureSource(
                    minecraft, level, selection, textures, options);
            StreamingBatchSink sink = new StreamingBatchSink(
                    transaction,
                    textures,
                    name,
                    metadataPlan,
                    rootExtras(minecraft, metadataPlan, options),
                    level.getGameTime(),
                    level::getGameTime,
                    telemetry);
            return new RollingExport(new ExportJob(
                    source, sink, System::nanoTime, Duration.ofMillis(6), telemetry),
                    source);
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
        String loaderVersion = loadedModVersion("fabricloader");
        ExportEnvironment environment = new ExportEnvironment(
                "26.2",
                "fabric",
                loaderVersion,
                "1.2.0-fabric-alpha.1",
                List.copyOf(minecraft.getResourcePackRepository().getSelectedIds()),
                FabricLoader.getInstance().getAllMods().stream()
                        .map(container -> container.getMetadata().getId() + "@"
                                + container.getMetadata().getVersion())
                        .sorted()
                        .toList());
        Map<String, Object> extras = new LinkedHashMap<>(environment.asExtras());
        extras.put("fabricLoaderVersion", loaderVersion);
        extras.put("dimension", selection.min().dimension());
        extras.put("selectionMin", List.of(
                selection.min().x(), selection.min().y(), selection.min().z()));
        extras.put("selectionMax", List.of(
                selection.max().x(), selection.max().y(), selection.max().z()));
        extras.put("origin", List.of(
                selection.min().x(), selection.min().y(), selection.min().z()));
        extras.put("snapshotMode", "rolling_client_snapshot");
        extras.put("formats", List.of("gltf", "usda"));
        extras.put("includePlayers", options.includePlayers());
        extras.put("sourceTopologyPreservedInUsda", true);
        return extras;
    }

    private static String loadedModVersion(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().toString())
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
                    atlasId -> minecraft.getAtlasManager().getAtlasOrThrow(atlasId));
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

    static Function<RenderTypeDescriptor, MaterialKey> resourceMaterialResolver(
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
                Identifier id = Identifier.parse(resourceId);
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

}
