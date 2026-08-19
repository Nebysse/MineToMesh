package com.nebysse.minetomesh.texture;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TextureImage(
        int width,
        int height,
        byte[] rgba,
        Optional<byte[]> sourcePng,
        Optional<byte[]> mcmeta,
        Optional<AnimationInfo> animation) {
    public TextureImage {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Texture dimensions must be positive");
        }
        Objects.requireNonNull(rgba, "rgba");
        int expectedLength = Math.multiplyExact(Math.multiplyExact(width, height), 4);
        if (rgba.length != expectedLength) {
            throw new IllegalArgumentException("RGBA payload length does not match texture dimensions");
        }
        rgba = rgba.clone();
        sourcePng = copyBytes(sourcePng);
        mcmeta = copyBytes(mcmeta);
        animation = Objects.requireNonNull(animation, "animation");
    }

    @Override
    public byte[] rgba() {
        return rgba.clone();
    }

    @Override
    public Optional<byte[]> sourcePng() {
        return copyBytes(sourcePng);
    }

    @Override
    public Optional<byte[]> mcmeta() {
        return copyBytes(mcmeta);
    }

    private static Optional<byte[]> copyBytes(Optional<byte[]> bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return bytes.map(byte[]::clone);
    }

    public record AnimationInfo(
            int frameWidth,
            int frameHeight,
            List<Integer> frameOrder,
            List<Integer> frameTimes,
            boolean interpolate) {
        public AnimationInfo {
            if (frameWidth <= 0 || frameHeight <= 0) {
                throw new IllegalArgumentException("Animation frame dimensions must be positive");
            }
            frameOrder = List.copyOf(frameOrder);
            frameTimes = List.copyOf(frameTimes);
            if (frameOrder.isEmpty() || frameOrder.size() != frameTimes.size()) {
                throw new IllegalArgumentException("Animation frame order and timing must be non-empty and aligned");
            }
            if (frameOrder.stream().anyMatch(index -> index < 0)
                    || frameTimes.stream().anyMatch(time -> time <= 0)) {
                throw new IllegalArgumentException("Animation frame indices and times must be positive values");
            }
        }
    }
}
