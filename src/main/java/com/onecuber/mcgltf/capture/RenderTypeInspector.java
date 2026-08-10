package com.onecuber.mcgltf.capture;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.onecuber.mcgltf.scene.Diagnostic;
import com.onecuber.mcgltf.scene.MaterialKey;
import com.onecuber.mcgltf.scene.PrimitiveMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

public final class RenderTypeInspector {
    private RenderTypeInspector() {
    }

    public static Inspection inspect(RenderType renderType) {
        Objects.requireNonNull(renderType, "renderType");
        String name = renderType.name.toLowerCase(Locale.ROOT);
        Optional<PrimitiveMode> mappedMode = primitiveMode(renderType.mode());
        boolean discard = mappedMode.isEmpty() || isDiscardedName(name) || renderType.isOutline();
        PrimitiveMode mode = mappedMode.orElse(PrimitiveMode.LINES);

        Optional<String> texture = Optional.empty();
        boolean cull = true;
        boolean transparentState = false;
        boolean additiveState = false;
        if (renderType instanceof RenderType.CompositeRenderType composite) {
            RenderType.CompositeState state = composite.state();
            texture = state.textureState.cutoutTexture().map(Object::toString);
            cull = state.cullState == RenderStateShard.CULL;
            transparentState = state.transparencyState != RenderStateShard.NO_TRANSPARENCY;
            additiveState = state.transparencyState == RenderStateShard.ADDITIVE_TRANSPARENCY
                    || state.transparencyState == RenderStateShard.LIGHTNING_TRANSPARENCY;
        }

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
                "RenderType semantics were inferred from composite state");
        return new Inspection(descriptor, List.of(diagnostic));
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
