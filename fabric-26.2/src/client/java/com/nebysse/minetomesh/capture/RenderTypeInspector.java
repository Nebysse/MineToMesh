package com.nebysse.minetomesh.capture;

import com.mojang.blaze3d.PrimitiveTopology;
import com.nebysse.minetomesh.scene.Diagnostic;
import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.PrimitiveMode;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public final class RenderTypeInspector {
    private static final Field NAME_FIELD = field(RenderType.class, "name");
    private static final Field STATE_FIELD = field(RenderType.class, "state");
    private static final Field TEXTURES_FIELD = field(RenderSetup.class, "textures");

    private RenderTypeInspector() {
    }

    public static Inspection inspect(RenderType renderType) {
        Objects.requireNonNull(renderType, "renderType");
        String name = name(renderType).toLowerCase(Locale.ROOT);
        Optional<PrimitiveMode> mappedMode = primitiveMode(
                renderType.primitiveTopology());
        boolean discard = mappedMode.isEmpty()
                || isDiscardedName(name)
                || renderType.isOutline();
        PrimitiveMode mode = mappedMode.orElse(PrimitiveMode.LINES);

        Optional<String> texture = texture(renderType).map(Identifier::toString);
        boolean cull = renderType.pipeline().isCull();
        boolean glint = name.contains("glint");
        boolean emissive = name.contains("eyes") || name.contains("emissive");
        boolean cutout = name.contains("cutout");
        boolean blend = renderType.hasBlending()
                || name.contains("translucent") || glint || emissive;
        MaterialKey.AlphaMode alphaMode = blend
                ? MaterialKey.AlphaMode.BLEND
                : cutout ? MaterialKey.AlphaMode.MASK : MaterialKey.AlphaMode.OPAQUE;
        Optional<Float> cutoff = alphaMode == MaterialKey.AlphaMode.MASK
                ? Optional.of(0.5F)
                : Optional.empty();
        boolean additive = name.contains("additive")
                || name.contains("lightning") || emissive;
        MaterialKey.BlendSemantic blendSemantic = glint
                ? MaterialKey.BlendSemantic.GLINT
                : additive
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
                "RenderType semantics were inferred from the 26.2 pipeline");
        return new Inspection(descriptor, List.of(diagnostic));
    }

    public static Optional<PrimitiveMode> primitiveMode(
            PrimitiveTopology topology) {
        Objects.requireNonNull(topology, "topology");
        return switch (topology) {
            case QUADS -> Optional.of(PrimitiveMode.QUADS);
            case TRIANGLES -> Optional.of(PrimitiveMode.TRIANGLES);
            case TRIANGLE_STRIP -> Optional.of(PrimitiveMode.TRIANGLE_STRIP);
            case TRIANGLE_FAN -> Optional.of(PrimitiveMode.TRIANGLE_FAN);
            case LINES -> Optional.of(PrimitiveMode.LINES);
            case DEBUG_LINES, DEBUG_LINE_STRIP, POINTS -> Optional.empty();
        };
    }

    private static String name(RenderType renderType) {
        try {
            return (String) NAME_FIELD.get(renderType);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot inspect RenderType name", exception);
        }
    }

    private static Optional<Identifier> texture(RenderType renderType) {
        try {
            RenderSetup setup = (RenderSetup) STATE_FIELD.get(renderType);
            @SuppressWarnings("unchecked")
            Map<String, Object> textures =
                    (Map<String, Object>) TEXTURES_FIELD.get(setup);
            for (Object binding : textures.values()) {
                Field location = field(binding.getClass(), "location");
                Object value = location.get(binding);
                if (value instanceof Identifier identifier) {
                    return Optional.of(identifier);
                }
            }
            return Optional.empty();
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot inspect RenderType texture", exception);
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

    public record Inspection(
            RenderTypeDescriptor descriptor,
            List<Diagnostic> diagnostics) {
        public Inspection {
            Objects.requireNonNull(descriptor, "descriptor");
            diagnostics = List.copyOf(diagnostics);
        }
    }
}
