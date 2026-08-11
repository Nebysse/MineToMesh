package com.nebysse.minetomesh.scene;

public record Vec2f(float x, float y) {
    public Vec2f {
        requireFinite(x, "x");
        requireFinite(y, "y");
    }

    private static void requireFinite(float value, String component) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Vector component " + component + " must be finite");
        }
    }
}
