package com.nebysse.minetomesh.scene;

import java.util.Objects;

public record Vertex(Vec3f position, Vec3f normal, Vec2f uv, ColorRgba color) {
    public Vertex {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(normal, "normal");
        Objects.requireNonNull(uv, "uv");
        Objects.requireNonNull(color, "color");
    }
}
