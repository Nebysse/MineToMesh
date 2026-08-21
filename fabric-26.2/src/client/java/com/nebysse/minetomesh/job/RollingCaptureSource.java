package com.nebysse.minetomesh.job;

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
import com.nebysse.minetomesh.scene.BatchCounters;
import com.nebysse.minetomesh.scene.CapturedNode;
import com.nebysse.minetomesh.scene.ChunkBatch;
import com.nebysse.minetomesh.scene.Diagnostic;
import com.nebysse.minetomesh.scene.GeometryAdjustmentStats;
import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.PrimitiveAccumulator;
import com.nebysse.minetomesh.texture.AtlasSpriteResolver;
import com.nebysse.minetomesh.texture.SpriteTextureExtractor;
import com.nebysse.minetomesh.texture.TextureRegistry;
import com.nebysse.minetomesh.world.ChunkCoordinate;
import com.nebysse.minetomesh.world.ExportPlan;
import com.nebysse.minetomesh.world.Selection;
import com.nebysse.minetomesh.world.WorldPlanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Session-scoped capture source whose unit inventory grows as the server
 * authorizes rolling batches. Units are captured strictly in enqueue order,
 * so {@link #capturedUnits()} tells the controller when a batch is fully
 * captured and safe to acknowledge.
 */
public final class RollingCaptureSource implements ExportJob.CaptureSource {
    private final ClientLevel level;
    private final Selection selection;
    private final ExportOptions options;
    private final BlockModelExtractor blocks;
    private final FluidGeometryCapture fluids;
    private final BlockEntityCapture blockEntities;
    private final EntityCapture entities;
    private final List<Diagnostic> materialDiagnostics = new ArrayList<>();
    private final List<Unit> units = new ArrayList<>();
    private boolean inputFinished;
    private int capturedUnits;

    public RollingCaptureSource(
            Minecraft minecraft,
            ClientLevel level,
            Selection selection,
            TextureRegistry textures,
            ExportOptions options) {
        this.level = Objects.requireNonNull(level, "level");
        this.selection = Objects.requireNonNull(selection, "selection");
        this.options = Objects.requireNonNull(options, "options");
        SpriteTextureExtractor sprites = new SpriteTextureExtractor(
                minecraft.getResourceManager());
        AtlasSpriteResolver atlasSprites = new AtlasSpriteResolver(
                atlasId -> minecraft.getAtlasManager().getAtlasOrThrow(atlasId));
        this.blocks = new BlockModelExtractor(sprites, atlasSprites, textures);
        this.fluids = new FluidGeometryCapture(sprites, textures);
        Function<RenderTypeDescriptor, MaterialKey> materialResolver =
                DefaultExportPipeline.resourceMaterialResolver(
                        minecraft, textures, materialDiagnostics);
        this.blockEntities = new BlockEntityCapture(materialResolver);
        this.entities = new EntityCapture(materialResolver);
    }

    public synchronized int enqueueBatch(List<ChunkCoordinate> chunks) {
        if (inputFinished) {
            throw new IllegalStateException("Rolling capture input is finished");
        }
        ExportPlan batchPlan = new WorldPlanner().planBatch(level, selection, chunks);
        int added = 0;
        for (ExportPlan.SectionWork work : batchPlan.sections()) {
            units.add(new SectionUnit(work));
            added++;
        }
        units.add(new EntitiesUnit(chunks));
        added++;
        return added;
    }

    public synchronized void finishInput() {
        inputFinished = true;
    }

    public synchronized int capturedUnits() {
        return capturedUnits;
    }

    @Override
    public synchronized int sectionCount() {
        return units.size();
    }

    @Override
    public synchronized boolean inputFinished() {
        return inputFinished;
    }

    @Override
    public ChunkBatch captureEntities() {
        List<Diagnostic> diagnostics = drainMaterialDiagnostics();
        return new ChunkBatch(List.of(), diagnostics, BatchCounters.ZERO);
    }

    @Override
    public ExportJob.SectionCapture openSection(int index) {
        Unit unit;
        synchronized (this) {
            unit = units.get(index);
        }
        return unit;
    }

    private List<Diagnostic> drainMaterialDiagnostics() {
        List<Diagnostic> drained;
        synchronized (this) {
            drained = List.copyOf(materialDiagnostics);
            materialDiagnostics.clear();
        }
        return drained;
    }

    private abstract class Unit implements ExportJob.SectionCapture {
        @Override
        public final ChunkBatch finish() {
            ChunkBatch batch = doFinish();
            synchronized (RollingCaptureSource.this) {
                capturedUnits++;
            }
            return batch;
        }

        abstract ChunkBatch doFinish();
    }

    private final class SectionUnit extends Unit {
        private final ExportPlan.SectionWork work;
        private final PrimitiveAccumulator accumulator;
        private final PrimitiveAccumulator overlayAccumulator;
        private final List<CapturedNode> nodes = new ArrayList<>();
        private final List<Diagnostic> diagnostics = new ArrayList<>();
        private BatchCounters counters = BatchCounters.ZERO;
        private GeometryAdjustmentStats adjustments = GeometryAdjustmentStats.ZERO;
        private long next;

        private SectionUnit(ExportPlan.SectionWork work) {
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
                    level, position, selection, accumulator, overlayAccumulator);
            counters = counters.plus(block.counters());
            adjustments = adjustments.plus(block.adjustments());
            diagnostics.addAll(block.diagnostics());

            FluidGeometryCapture.CaptureResult fluid = fluids.capture(
                    level, position, selection, accumulator);
            counters = counters.plus(fluid.counters());
            diagnostics.addAll(fluid.diagnostics());

            BlockEntity blockEntity = level.getBlockEntity(position);
            if (blockEntity != null) {
                BlockEntityCapture.CaptureResult rendered = blockEntities.capture(
                        blockEntity, selection);
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
        ChunkBatch doFinish() {
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

        private BatchCounters placeholderCounter() {
            return new BatchCounters(0, 0, 0, 0, 0, 0, 0, 12, 1);
        }

        private CapturedNode blockPlaceholder(BlockPos position) {
            CaptureCoordinates.Bounds bounds = CaptureCoordinates.blockBounds(
                    position, selection);
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

    private final class EntitiesUnit extends Unit {
        private final List<ChunkCoordinate> chunks;
        private final List<CapturedNode> nodes = new ArrayList<>();
        private final List<Diagnostic> diagnostics = new ArrayList<>();
        private BatchCounters counters = BatchCounters.ZERO;
        private boolean captured;

        private EntitiesUnit(List<ChunkCoordinate> chunks) {
            this.chunks = List.copyOf(chunks);
        }

        @Override
        public String objectId() {
            return "entities";
        }

        @Override
        public boolean hasNext() {
            return !captured;
        }

        @Override
        public void captureNext() {
            captured = true;
            List<Entity> collected = entities.collectInChunks(
                    level, selection, options.includePlayers(), chunks);
            for (Entity entity : collected) {
                EntityCapture.ObjectResult result = entities.capture(entity, selection);
                result.node().ifPresent(nodes::add);
                diagnostics.addAll(result.diagnostics());
                counters = counters.plus(result.counters());
            }
        }

        @Override
        ChunkBatch doFinish() {
            diagnostics.addAll(drainMaterialDiagnostics());
            return new ChunkBatch(nodes, diagnostics, counters);
        }
    }
}
