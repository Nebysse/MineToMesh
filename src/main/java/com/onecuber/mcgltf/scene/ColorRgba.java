package com.onecuber.mcgltf.scene;

public record ColorRgba(int red, int green, int blue, int alpha) {
    public static final ColorRgba WHITE = new ColorRgba(255, 255, 255, 255);

    public ColorRgba {
        requireByte(red, "red");
        requireByte(green, "green");
        requireByte(blue, "blue");
        requireByte(alpha, "alpha");
    }

    private static void requireByte(int value, String channel) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("Color channel " + channel + " must be in range 0..255");
        }
    }
}
