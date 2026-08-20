package com.nebysse.minetomesh.capture;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nebysse.minetomesh.scene.Diagnostic;
import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.PrimitiveAccumulator;
import com.nebysse.minetomesh.scene.PrimitiveData;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class CapturingMultiBufferSource {
    private final String objectId;
    private final Function<RenderTypeDescriptor, MaterialKey> materialResolver;
    private final Map<RenderType, Entry> entriesByIdentity = new IdentityHashMap<>();
    private final List<Entry> entriesInFirstUseOrder = new ArrayList<>();
    private CaptureResult finishedResult;

    public CapturingMultiBufferSource(
            String objectId,
            Function<RenderTypeDescriptor, MaterialKey> materialResolver) {
        this.objectId = Objects.requireNonNull(objectId, "objectId");
        this.materialResolver = Objects.requireNonNull(
                materialResolver, "materialResolver");
    }

    public VertexConsumer getBuffer(RenderType renderType) {
        if (finishedResult != null) {
            throw new IllegalStateException("Buffer source is already finished");
        }
        Entry existing = entriesByIdentity.get(renderType);
        if (existing != null) {
            return existing.consumer;
        }
        Entry created = new Entry(
                renderType,
                RenderTypeInspector.inspect(renderType),
                new CapturingVertexConsumer());
        entriesByIdentity.put(renderType, created);
        entriesInFirstUseOrder.add(created);
        return created.consumer;
    }

    public CaptureResult finishAll() {
        if (finishedResult != null) {
            return finishedResult;
        }
        PrimitiveAccumulator accumulator = new PrimitiveAccumulator(objectId);
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (Entry entry : entriesInFirstUseOrder) {
            List<com.nebysse.minetomesh.scene.Vertex> vertices =
                    entry.consumer.finish();
            RenderTypeDescriptor descriptor = entry.inspection.descriptor();
            diagnostics.addAll(entry.inspection.diagnostics());
            if (!descriptor.discard() && !vertices.isEmpty()) {
                accumulator.append(
                        materialResolver.apply(descriptor),
                        descriptor.primitiveMode(),
                        vertices);
            }
        }
        PrimitiveAccumulator.SealResult sealed = accumulator.seal();
        diagnostics.addAll(sealed.diagnostics());
        finishedResult = new CaptureResult(sealed.primitives(), diagnostics);
        entriesByIdentity.clear();
        entriesInFirstUseOrder.clear();
        return finishedResult;
    }

    private record Entry(
            RenderType renderType,
            RenderTypeInspector.Inspection inspection,
            CapturingVertexConsumer consumer) {
    }

    public record CaptureResult(
            List<PrimitiveData> primitives,
            List<Diagnostic> diagnostics) {
        public CaptureResult {
            primitives = List.copyOf(primitives);
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
