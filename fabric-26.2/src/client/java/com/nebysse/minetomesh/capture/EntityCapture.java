package com.nebysse.minetomesh.capture;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nebysse.minetomesh.backend.FabricRenderBackends;
import com.nebysse.minetomesh.material.MaterialResolver;
import com.nebysse.minetomesh.scene.BatchCounters;
import com.nebysse.minetomesh.scene.CapturedNode;
import com.nebysse.minetomesh.scene.Diagnostic;
import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.PrimitiveData;
import com.nebysse.minetomesh.scene.Vec3f;
import com.nebysse.minetomesh.world.ChunkCoordinate;
import com.nebysse.minetomesh.world.Selection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class EntityCapture {
    private static final double PLACEHOLDER_INFLATION = 0.01D;

    private final Function<RenderTypeDescriptor, MaterialKey> materialResolver;
    private final RendererReplay rendererReplay;

    public EntityCapture() {
        this(MaterialResolver::resolve);
    }

    public EntityCapture(Function<RenderTypeDescriptor, MaterialKey> materialResolver) {
        this(materialResolver, new RendererReplay(FabricRenderBackends.discover(
                EntityCapture.class.getClassLoader())));
    }

    public EntityCapture(
            Function<RenderTypeDescriptor, MaterialKey> materialResolver,
            RendererReplay rendererReplay) {
        this.materialResolver = Objects.requireNonNull(materialResolver, "materialResolver");
        this.rendererReplay = Objects.requireNonNull(rendererReplay, "rendererReplay");
    }

    public List<Entity> collect(
            ClientLevel level, Selection selection, boolean includePlayers) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(selection, "selection");
        AABB bounds = selectionBounds(selection);
        List<Entity> entities = new ArrayList<>(level.getEntities(
                (Entity) null,
                bounds,
                entity -> shouldInclude(category(entity), includePlayers, entity.isRemoved(),
                        entity.getBoundingBox(), bounds)));
        entities.sort(Comparator
                .comparing(EntityCapture::registryId)
                .thenComparing(Entity::getUUID));
        return List.copyOf(entities);
    }

    public List<Entity> collectInChunks(
            ClientLevel level,
            Selection selection,
            boolean includePlayers,
            List<ChunkCoordinate> chunks) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(chunks, "chunks");
        Set<Long> members = new HashSet<>();
        for (ChunkCoordinate chunk : chunks) {
            members.add(net.minecraft.world.level.ChunkPos.pack(chunk.x(), chunk.z()));
        }
        AABB bounds = selectionBounds(selection);
        List<Entity> entities = new ArrayList<>(level.getEntities(
                (Entity) null,
                bounds,
                entity -> members.contains(entity.chunkPosition().pack())
                        && shouldInclude(category(entity), includePlayers, entity.isRemoved(),
                                entity.getBoundingBox(), bounds)));
        entities.sort(Comparator
                .comparing(EntityCapture::registryId)
                .thenComparing(Entity::getUUID));
        return List.copyOf(entities);
    }

    public CaptureResult captureAll(
            ClientLevel level, Selection selection, boolean includePlayers) {
        List<CapturedNode> nodes = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        BatchCounters counters = BatchCounters.ZERO;
        for (Entity entity : collect(level, selection, includePlayers)) {
            ObjectResult result = capture(entity, selection);
            result.node().ifPresent(nodes::add);
            diagnostics.addAll(result.diagnostics());
            counters = counters.plus(result.counters());
        }
        return new CaptureResult(nodes, diagnostics, counters);
    }

    public ObjectResult capture(Entity entity, Selection selection) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(selection, "selection");
        String registryId = registryId(entity);
        String uuid = entity.getUUID().toString();
        String objectId = registryId + "/" + uuid;
        EntityRenderer<Entity, EntityRenderState> renderer = renderer(entity);
        if (renderer == null) {
            return fallback(entity, selection, objectId, registryId, uuid, null,
                    "ENTITY_RENDERER_MISSING", "No entity renderer is registered", null);
        }

        CapturingSubmitNodeCollector buffers = new CapturingSubmitNodeCollector(
                objectId, materialResolver);
        try {
            EntityRenderState renderState = renderer.createRenderState(entity, 0.0F);
            PoseStack poseStack = entityPose(
                    entity, selection, renderer, renderState);
            CameraRenderState camera = new CameraRenderState();
            camera.pos = Vec3.ZERO;
            camera.initialized = true;
            RendererReplay.Outcome replay = rendererReplay.run(() ->
                    renderer.submit(renderState, poseStack, buffers, camera));
            if (!replay.success()) {
                Exception exception = replay.failure().orElseThrow();
                String code = replay.failureStage() == RendererReplay.FailureStage.BACKEND
                        || replay.failureStage() == RendererReplay.FailureStage.RESTORE
                        ? "RENDER_BACKEND_FALLBACK_FAILED" : "ENTITY_CAPTURE_FAILED";
                return fallback(entity, selection, objectId, registryId, uuid,
                        renderer.getClass().getName(), code,
                        exception.getMessage() == null ? code : exception.getMessage(),
                        exception);
            }
            CapturingMultiBufferSource.CaptureResult capture = buffers.finishAll();
            List<Diagnostic> captureDiagnostics = new ArrayList<>(capture.diagnostics());
            if (replay.fallbackUsed()) {
                captureDiagnostics.add(diagnostic(
                        Diagnostic.Severity.INFO,
                        "RENDER_BACKEND_FALLBACK_USED",
                        objectId,
                        renderer.getClass().getName(),
                        "",
                        replay.adapterId()));
            }
            Map<String, Object> extras = extras(
                    entity, selection, registryId, uuid, renderer.getClass().getName());
            if (hasGeometry(capture.primitives())) {
                CapturedNode node = new CapturedNode(
                        objectId,
                        CapturedNode.Kind.ENTITY,
                        capture.primitives(),
                        extras);
                long triangles = triangleCount(capture.primitives());
                return new ObjectResult(
                        Optional.of(node),
                        captureDiagnostics,
                        new BatchCounters(0, 0, 0, 0, 1, 0, 0, triangles, 0));
            }
            if (isVisibleNonMarker(entity)) {
                ObjectResult fallback = fallback(entity, selection, objectId, registryId, uuid,
                        renderer.getClass().getName(), "ENTITY_ZERO_VERTICES",
                        "Entity renderer emitted no exportable vertices", null);
                List<Diagnostic> merged = new ArrayList<>(captureDiagnostics);
                merged.addAll(fallback.diagnostics());
                return new ObjectResult(fallback.node(), merged, fallback.counters());
            }
            List<Diagnostic> skipped = new ArrayList<>(captureDiagnostics);
            skipped.add(diagnostic(Diagnostic.Severity.WARNING, "ENTITY_ZERO_VERTICES_SKIPPED",
                    objectId, renderer.getClass().getName(), "",
                    "Invisible or marker entity emitted no exportable vertices"));
            return new ObjectResult(Optional.empty(), skipped, BatchCounters.ZERO);
        } catch (Exception exception) {
            return fallback(entity, selection, objectId, registryId, uuid,
                    renderer.getClass().getName(), "ENTITY_CAPTURE_FAILED",
                    exception.getMessage() == null ? "Entity capture failed" : exception.getMessage(),
                    exception);
        }
    }

    public static boolean shouldInclude(
            EntityCategory category,
            boolean includePlayers,
            boolean removed,
            AABB entityBounds,
            AABB selectionBounds) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(entityBounds, "entityBounds");
        Objects.requireNonNull(selectionBounds, "selectionBounds");
        return (includePlayers || category != EntityCategory.PLAYER)
                && !removed
                && intersectsInclusive(entityBounds, selectionBounds);
    }

    public static AABB selectionBounds(Selection selection) {
        return new AABB(
                selection.min().x(), selection.min().y(), selection.min().z(),
                (double) selection.max().x() + 1.0D,
                (double) selection.max().y() + 1.0D,
                (double) selection.max().z() + 1.0D);
    }

    private static boolean intersectsInclusive(AABB left, AABB right) {
        return left.maxX >= right.minX && left.minX <= right.maxX
                && left.maxY >= right.minY && left.minY <= right.maxY
                && left.maxZ >= right.minZ && left.minZ <= right.maxZ;
    }

    private ObjectResult fallback(
            Entity entity,
            Selection selection,
            String objectId,
            String registryId,
            String uuid,
            String rendererClass,
            String code,
            String message,
            Exception exception) {
        Map<String, Object> extras = extras(entity, selection, registryId, uuid, rendererClass);
        extras.put("fallbackReason", code);
        AABB box = entity.getBoundingBox().inflate(PLACEHOLDER_INFLATION);
        CaptureCoordinates.Bounds bounds = CaptureCoordinates.localBounds(box, selection);
        CapturedNode placeholder = PlaceholderFactory.create(
                objectId, bounds.min(), bounds.max(), extras);
        Diagnostic diagnostic = diagnostic(
                Diagnostic.Severity.FAILURE,
                code,
                objectId,
                rendererClass == null ? "" : rendererClass,
                exception == null ? "" : exception.getClass().getName(),
                message);
        return new ObjectResult(
                Optional.of(placeholder),
                List.of(diagnostic),
                new BatchCounters(0, 0, 0, 0, 1, 0, 0, 12, 1));
    }

    private static PoseStack entityPose(
            Entity entity,
            Selection selection,
            EntityRenderer<Entity, EntityRenderState> renderer,
            EntityRenderState renderState) {
        Vec3 offset = renderer.getRenderOffset(renderState);
        return CaptureCoordinates.translatedPose(CaptureCoordinates.localPosition(
                entity.getX() + offset.x,
                entity.getY() + offset.y,
                entity.getZ() + offset.z,
                selection));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static EntityRenderer<Entity, EntityRenderState> renderer(Entity entity) {
        EntityRenderDispatcher dispatcher = Minecraft.getInstance()
                .getEntityRenderDispatcher();
        return (EntityRenderer) dispatcher.getRenderer(entity);
    }

    private static Map<String, Object> extras(
            Entity entity,
            Selection selection,
            String registryId,
            String uuid,
            String rendererClass) {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("registryId", registryId);
        extras.put("uuid", uuid);
        extras.put("worldPosition", List.of(entity.getX(), entity.getY(), entity.getZ()));
        Vec3f local = CaptureCoordinates.localPosition(
                entity.getX(), entity.getY(), entity.getZ(), selection);
        extras.put("localPosition", List.of(local.x(), local.y(), local.z()));
        extras.put("rendererClass", rendererClass == null ? "" : rendererClass);
        return extras;
    }

    private static EntityCategory category(Entity entity) {
        if (entity instanceof Player) {
            return EntityCategory.PLAYER;
        }
        if (entity instanceof ArmorStand) {
            return EntityCategory.ARMOR_STAND;
        }
        if (entity instanceof ItemEntity) {
            return EntityCategory.ITEM;
        }
        if (entity instanceof net.minecraft.world.entity.LivingEntity) {
            return EntityCategory.LIVING;
        }
        if (entity.isVehicle()) {
            return EntityCategory.VEHICLE;
        }
        return EntityCategory.OTHER;
    }

    private static boolean isVisibleNonMarker(Entity entity) {
        return !entity.isInvisible()
                && !(entity instanceof Marker)
                && !(entity instanceof ArmorStand armorStand && armorStand.isMarker());
    }

    private static String registryId(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    private static boolean hasGeometry(List<PrimitiveData> primitives) {
        return primitives.stream().anyMatch(primitive -> primitive.indices().length > 0);
    }

    private static long triangleCount(List<PrimitiveData> primitives) {
        long count = 0;
        for (PrimitiveData primitive : primitives) {
            int indexCount = primitive.indices().length;
            count += switch (primitive.gltfMode()) {
                case 4 -> indexCount / 3L;
                case 5, 6 -> Math.max(0, indexCount - 2L);
                default -> 0L;
            };
        }
        return count;
    }

    private static Diagnostic diagnostic(
            Diagnostic.Severity severity,
            String code,
            String objectId,
            String rendererClass,
            String exceptionClass,
            String message) {
        return new Diagnostic(severity, code, objectId, Optional.empty(),
                rendererClass, exceptionClass, message);
    }

    public enum EntityCategory {
        PLAYER,
        LIVING,
        VEHICLE,
        ARMOR_STAND,
        ITEM,
        OTHER
    }

    public record ObjectResult(
            Optional<CapturedNode> node,
            List<Diagnostic> diagnostics,
            BatchCounters counters) {
        public ObjectResult {
            node = Objects.requireNonNull(node, "node");
            diagnostics = List.copyOf(diagnostics);
            Objects.requireNonNull(counters, "counters");
        }
    }

    public record CaptureResult(
            List<CapturedNode> nodes,
            List<Diagnostic> diagnostics,
            BatchCounters counters) {
        public CaptureResult {
            nodes = List.copyOf(nodes);
            diagnostics = List.copyOf(diagnostics);
            Objects.requireNonNull(counters, "counters");
        }
    }
}
