package com.onecuber.mcgltf.scene;

public record Vec3f(float x, float y, float z) {
    public static final Vec3f UP = new Vec3f(0.0F, 1.0F, 0.0F);

    public Vec3f {
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
    }

    public Vec3f normalizedOrUp() {
        double lengthSquared = (double) x * x + (double) y * y + (double) z * z;
        if (lengthSquared == 0.0D) {
            return UP;
        }
        float inverseLength = (float) (1.0D / Math.sqrt(lengthSquared));
        return new Vec3f(x * inverseLength, y * inverseLength, z * inverseLength);
    }

    private static void requireFinite(float value, String component) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Vector component " + component + " must be finite");
        }
    }
}
