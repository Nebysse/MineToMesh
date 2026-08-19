package com.nebysse.minetomesh.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CoordinateTransformTest {
    @Test
    void subtractsOriginWithoutReflectingHorizontalAxes() {
        CoordinateTransform transform = new CoordinateTransform(
                new Vec3f(10.0F, 64.0F, -5.0F));

        assertEquals(new Vec3f(2.5F, 2.0F, 2.0F),
                transform.position(new Vec3f(12.5F, 66.0F, -3.0F)));
        assertEquals(new Vec3f(1.0F, 0.0F, 0.0F),
                transform.position(new Vec3f(11.0F, 64.0F, -5.0F)));
        assertEquals(new Vec3f(0.0F, 0.0F, 1.0F),
                transform.position(new Vec3f(10.0F, 64.0F, -4.0F)));
    }

    @Test
    void preservesAndNormalizesNormals() {
        CoordinateTransform transform = new CoordinateTransform(
                new Vec3f(0.0F, 0.0F, 0.0F));

        assertEquals(new Vec3f(0.0F, 0.0F, 1.0F),
                transform.normal(new Vec3f(0.0F, 0.0F, 2.0F)));
        assertEquals(Vec3f.UP,
                transform.normal(new Vec3f(0.0F, 0.0F, 0.0F)));
    }
}
