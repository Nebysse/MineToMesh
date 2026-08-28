package com.nebysse.minetomesh.capture;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nebysse.minetomesh.scene.Diagnostic;
import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.PrimitiveMode;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

public final class RenderTypeInspector {
    // 1.21.10 起 name 收敛为 protected、CompositeState 失去访问器，只能靠反射读取。
    private static final Field NAME_FIELD = field(RenderStateShard.class, "name");
    private static final Field STATE_FIELD = field(RenderType.CompositeRenderType.class, "state");

    private RenderTypeInspector() {
    }

    public static Inspection inspect(RenderType renderType) {
        Objects.requireNonNull(renderType, "renderType");
        String name = name(renderType).toLowerCase(Locale.ROOT);
        Optional<PrimitiveMode> mappedMode = primitiveMode(renderType.mode());
        boolean discard = mappedMode.isEmpty() || isDiscardedName(name) || renderType.isOutline();
        PrimitiveMode mode = mappedMode.orElse(PrimitiveMode.LINES);

        Optional<String> texture = Optional.empty();
        if (renderType instanceof RenderType.CompositeRenderType composite) {
            RenderType.CompositeState state = state(composite);
            texture = state.textureState.cutoutTexture().map(Object::toString);
        }

        boolean cull = renderType.pipeline().isCull();
        Optional<BlendFunction> blendFunction = renderType.pipeline().getBlendFunction();
        boolean transparentState = blendFunction.isPresent();
        boolean additiveState = blendFunction
                .map(blend -> blend.equals(BlendFunction.ADDITIVE)
                        || blend.equals(BlendFunction.LIGHTNING))
                .orElse(false);

        boolean glint = name.contains("glint");
        boolean emissive = name.contains("eyes") || name.contains("emissive");
        boolean cutout = name.contains("cutout");
        boolean blend = name.contains("translucent") || glint || emissive
                || additiveState || transparentState;
        MaterialKey.AlphaMode alphaMode = blend
                ? MaterialKey.AlphaMode.BLEND
                : cutout ? MaterialKey.AlphaMode.MASK : MaterialKey.AlphaMode.OPAQUE;
        Optional<Float> cutoff = alphaMode == MaterialKey.AlphaMode.MASK
                ? Optional.of(0.5F)
                : Optional.empty();
        MaterialKey.BlendSemantic blendSemantic = glint
                ? MaterialKey.BlendSemantic.GLINT
                : additiveState || emissive
                        ? MaterialKey.BlendSemantic.ADDITIVE
                        : MaterialKey.BlendSemantic.STANDARD;
        boolean mipmap = name.equals("solid") || name.contains("mipped");

        RenderTypeDescriptor descriptor = new RenderTypeDescriptor(
                name, mode, texture, alphaMode, cutoff, cull, emissive,
                blendSemantic, mipmap, discard);
        if (discard || isRecognizedName(name)) {
            return new Inspection(descriptor, List.of());
        }
        Diagnostic diagnostic = new Diagnostic(
                Diagnostic.Severity.WARNING,
                "UNKNOWN_RENDER_TYPE",
                name,
                Optional.empty(),
                renderType.getClass().getName(),
                "",
                "RenderType semantics were inferred from the 1.21.10 pipeline");
        return new Inspection(descriptor, List.of(diagnostic));
    }

    /**
     * 1.21.10 的区块网格按 {@link ChunkSectionLayer} 分层，不再经过 RenderType 合成状态。
     */
    public static Inspection inspectLayer(ChunkSectionLayer layer) {
        Objects.requireNonNull(layer, "layer");
        MaterialKey.AlphaMode alphaMode = switch (layer) {
            case SOLID -> MaterialKey.AlphaMode.OPAQUE;
            case CUTOUT, CUTOUT_MIPPED, TRIPWIRE -> MaterialKey.AlphaMode.MASK;
            case TRANSLUCENT -> MaterialKey.AlphaMode.BLEND;
        };
        boolean mipmap = layer == ChunkSectionLayer.SOLID
                || layer == ChunkSectionLayer.CUTOUT_MIPPED
                || layer == ChunkSectionLayer.TRIPWIRE;
        RenderTypeDescriptor descriptor = new RenderTypeDescriptor(
                layer.label(), PrimitiveMode.QUADS,
                Optional.empty(), alphaMode,
                alphaMode == MaterialKey.AlphaMode.MASK
                        ? Optional.of(0.5F) : Optional.empty(),
                layer.pipeline().isCull(), false,
                MaterialKey.BlendSemantic.STANDARD, mipmap, false);
        return new Inspection(descriptor, List.of());
    }

    public static Optional<PrimitiveMode> primitiveMode(VertexFormat.Mode mode) {
        Objects.requireNonNull(mode, "mode");
        return switch (mode) {
            case QUADS -> Optional.of(PrimitiveMode.QUADS);
            case TRIANGLES -> Optional.of(PrimitiveMode.TRIANGLES);
            case TRIANGLE_STRIP -> Optional.of(PrimitiveMode.TRIANGLE_STRIP);
            case TRIANGLE_FAN -> Optional.of(PrimitiveMode.TRIANGLE_FAN);
            case LINES -> Optional.of(PrimitiveMode.LINES);
            case LINE_STRIP -> Optional.of(PrimitiveMode.LINE_STRIP);
            case DEBUG_LINES, DEBUG_LINE_STRIP -> Optional.empty();
        };
    }

    private static String name(RenderType renderType) {
        try {
            return (String) NAME_FIELD.get(renderType);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot inspect RenderType name", exception);
        }
    }

    private static RenderType.CompositeState state(RenderType.CompositeRenderType renderType) {
        try {
            return (RenderType.CompositeState) STATE_FIELD.get(renderType);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot inspect RenderType composite state", exception);
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static boolean isDiscardedName(String name) {
        return name.contains("text")
                || name.contains("shadow")
                || name.contains("outline")
                || name.contains("fire")
                || name.contains("debug");
    }

    private static boolean isRecognizedName(String name) {
        return name.equals("solid")
                || name.contains("cutout")
                || name.contains("translucent")
                || name.contains("entity")
                || name.contains("armor")
                || name.contains("eyes")
                || name.contains("emissive")
                || name.contains("glint")
                || name.contains("leash")
                || name.contains("lightning")
                || name.contains("water_mask")
                || name.contains("tripwire");
    }

    public record Inspection(RenderTypeDescriptor descriptor, List<Diagnostic> diagnostics) {
        public Inspection {
            Objects.requireNonNull(descriptor, "descriptor");
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
