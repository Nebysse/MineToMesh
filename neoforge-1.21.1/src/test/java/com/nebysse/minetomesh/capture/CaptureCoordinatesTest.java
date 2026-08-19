package com.nebysse.minetomesh.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nebysse.minetomesh.scene.Vec3f;
import com.nebysse.minetomesh.world.BlockPoint;
import com.nebysse.minetomesh.world.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class CaptureCoordinatesTest {
    private static final float EPSILON = 1.0E-6F;

    @Test
    void preservesPositiveZInLocalPositionsAndTranslationOnlyPoses() {
        Selection selection = selectionWithMinimum(10, 64, 20);
        Vec3f local = CaptureCoordinates.localPosition(
                12.0D, 67.0D, 24.0D, selection);
        PoseStack pose = CaptureCoordinates.translatedPose(local);
        Vector3f transformedOrigin = pose.last().pose()
                .transformPosition(new Vector3f(0.0F, 0.0F, 0.0F));
        Vector3f transformedNormal = pose.last().normal()
                .transform(new Vector3f(0.0F, 0.0F, 1.0F));

        assertEquals(new Vec3f(2.0F, 3.0F, 4.0F), local);
        assertVectorEquals(new Vector3f(2.0F, 3.0F, 4.0F), transformedOrigin);
        assertVectorEquals(new Vector3f(0.0F, 0.0F, 1.0F), transformedNormal);
        assertTrue(pose.last().pose().determinant3x3() > 0.0F);
    }

    @Test
    void preservesAscendingPositiveZBoundsForEntitiesAndBlocks() {
        Selection selection = selectionWithMinimum(10, 64, 20);
        CaptureCoordinates.Bounds entity = CaptureCoordinates.localBounds(
                new AABB(11.0D, 65.0D, 22.0D, 13.0D, 68.0D, 25.0D),
                selection);
        CaptureCoordinates.Bounds block = CaptureCoordinates.blockBounds(
                new BlockPos(12, 66, 24), selection);

        assertEquals(new Vec3f(1.0F, 1.0F, 2.0F), entity.min());
        assertEquals(new Vec3f(3.0F, 4.0F, 5.0F), entity.max());
        assertEquals(new Vec3f(2.0F, 2.0F, 4.0F), block.min());
        assertEquals(new Vec3f(3.0F, 3.0F, 5.0F), block.max());
    }

    private static Selection selectionWithMinimum(int x, int y, int z) {
        String dimension = "minecraft:overworld";
        return new Selection(
                new BlockPoint(dimension, x, y, z),
                new BlockPoint(dimension, x + 15, y + 15, z + 15));
    }

    private static void assertVectorEquals(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}
