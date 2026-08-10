package com.onecuber.mcgltf.capture;

import com.onecuber.mcgltf.scene.CapturedNode;
import com.onecuber.mcgltf.scene.ColorRgba;
import com.onecuber.mcgltf.scene.MaterialKey;
import com.onecuber.mcgltf.scene.PrimitiveData;
import com.onecuber.mcgltf.scene.PrimitiveMode;
import com.onecuber.mcgltf.scene.TextureKey;
import com.onecuber.mcgltf.scene.Vec2f;
import com.onecuber.mcgltf.scene.Vec3f;
import com.onecuber.mcgltf.scene.Vertex;
import com.onecuber.mcgltf.texture.TextureImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class PlaceholderFactory {
    public static final TextureKey TEXTURE = new TextureKey(
            TextureKey.Kind.DYNAMIC,
            "mcgltf:placeholder_white",
            "textures/mcgltf/generated/placeholder_white.png");
    public static final MaterialKey MATERIAL = new MaterialKey(
            TEXTURE,
            MaterialKey.AlphaMode.BLEND,
            Optional.empty(),
            true,
            false,
            MaterialKey.BlendSemantic.STANDARD,
            MaterialKey.SamplerMode.NEAREST);
    private static final ColorRgba MAGENTA_HALF_ALPHA = new ColorRgba(255, 0, 255, 128);
    private static final Vec2f UV = new Vec2f(0.5F, 0.5F);
    private static final int[] INDICES = {
        0, 2, 1, 0, 3, 2,
        4, 5, 6, 4, 6, 7,
        0, 4, 7, 0, 7, 3,
        1, 2, 6, 1, 6, 5,
        0, 1, 5, 0, 5, 4,
        3, 7, 6, 3, 6, 2
    };

    private PlaceholderFactory() {
    }

    public static CapturedNode create(
            String name,
            Vec3f min,
            Vec3f max,
            Map<String, Object> extras) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        Objects.requireNonNull(extras, "extras");
        if (!(max.x() > min.x()) || !(max.y() > min.y()) || !(max.z() > min.z())) {
            throw new IllegalArgumentException("Placeholder AABB must have positive volume");
        }

        float centerX = (min.x() + max.x()) * 0.5F;
        float centerY = (min.y() + max.y()) * 0.5F;
        float centerZ = (min.z() + max.z()) * 0.5F;
        List<Vec3f> positions = List.of(
                new Vec3f(min.x(), min.y(), min.z()),
                new Vec3f(max.x(), min.y(), min.z()),
                new Vec3f(max.x(), max.y(), min.z()),
                new Vec3f(min.x(), max.y(), min.z()),
                new Vec3f(min.x(), min.y(), max.z()),
                new Vec3f(max.x(), min.y(), max.z()),
                new Vec3f(max.x(), max.y(), max.z()),
                new Vec3f(min.x(), max.y(), max.z()));
        List<Vertex> vertices = new ArrayList<>(positions.size());
        for (Vec3f position : positions) {
            Vec3f normal = new Vec3f(
                    position.x() - centerX,
                    position.y() - centerY,
                    position.z() - centerZ).normalizedOrUp();
            vertices.add(new Vertex(position, normal, UV, MAGENTA_HALF_ALPHA));
        }
        List<Vertex> triangleVertices = java.util.Arrays.stream(INDICES)
                .mapToObj(vertices::get)
                .toList();
        PrimitiveData primitive = new PrimitiveData(
                triangleVertices,
                PrimitiveMode.TRIANGLES,
                new int[] {triangleVertices.size()},
                MATERIAL);
        return new CapturedNode(name, CapturedNode.Kind.PLACEHOLDER, List.of(primitive), extras);
    }

    public static TextureImage textureImage() {
        return new TextureImage(
                1,
                1,
                new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255},
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}
