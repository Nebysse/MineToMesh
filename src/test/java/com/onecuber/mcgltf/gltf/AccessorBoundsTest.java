package com.onecuber.mcgltf.gltf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.onecuber.mcgltf.scene.ColorRgba;
import com.onecuber.mcgltf.scene.Vec2f;
import com.onecuber.mcgltf.scene.Vec3f;
import com.onecuber.mcgltf.scene.Vertex;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccessorBoundsTest {
    @Test
    void computesPositionMinimumAndMaximum() {
        AccessorBounds bounds = AccessorBounds.positions(List.of(
                vertex(0.0F, 0.0F, -1.0F),
                vertex(2.0F, 3.0F, 4.0F)));

        assertArrayEquals(new float[] {0.0F, 0.0F, -1.0F}, bounds.min());
        assertArrayEquals(new float[] {2.0F, 3.0F, 4.0F}, bounds.max());
    }

    @Test
    void rejectsEmptyAndNonFiniteRawValues() {
        assertThrows(IllegalArgumentException.class, () -> AccessorBounds.positions(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> AccessorBounds.of(new float[] {0.0F, Float.NaN, 1.0F}, 3));
        assertThrows(IllegalArgumentException.class,
                () -> AccessorBounds.of(new float[] {0.0F, Float.POSITIVE_INFINITY, 1.0F}, 3));
    }

    private static Vertex vertex(float x, float y, float z) {
        return new Vertex(new Vec3f(x, y, z), Vec3f.UP, new Vec2f(0.0F, 0.0F), ColorRgba.WHITE);
    }
}
