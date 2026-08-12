package com.nebysse.minetomesh.scene;

import java.util.Objects;

public final class CoordinateTransform {
    private final Vec3f origin;

    public CoordinateTransform(Vec3f origin) {
        this.origin = Objects.requireNonNull(origin, "origin");
    }

    public Vec3f position(Vec3f world) {
        Objects.requireNonNull(world, "world");
        return new Vec3f(
                world.x() - origin.x(),
                world.y() - origin.y(),
                world.z() - origin.z());
    }

    public Vec3f normal(Vec3f value) {
        Objects.requireNonNull(value, "value");
        return value.normalizedOrUp();
    }
}
