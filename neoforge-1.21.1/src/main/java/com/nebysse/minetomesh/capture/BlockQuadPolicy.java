package com.nebysse.minetomesh.capture;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

public final class BlockQuadPolicy {
    private static final List<Direction> DIRECTIONS = Collections.unmodifiableList(Arrays.asList(
            Direction.DOWN,
            Direction.UP,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST,
            null));

    private BlockQuadPolicy() {
    }

    public static List<Direction> directions() {
        return DIRECTIONS;
    }

    public static void forEachDirection(
            long seed,
            BiConsumer<Direction, RandomSource> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        RandomSource random = RandomSource.create();
        for (Direction direction : DIRECTIONS) {
            random.setSeed(seed);
            consumer.accept(direction, random);
        }
    }

    public static boolean shouldRenderFace(
            boolean neighborInsideSelection,
            boolean neighborChunkLoaded,
            BooleanSupplier vanillaDecision) {
        Objects.requireNonNull(vanillaDecision, "vanillaDecision");
        return !neighborInsideSelection || !neighborChunkLoaded || vanillaDecision.getAsBoolean();
    }
}
