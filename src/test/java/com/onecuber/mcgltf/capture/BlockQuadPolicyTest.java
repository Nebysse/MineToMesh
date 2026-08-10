package com.onecuber.mcgltf.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class BlockQuadPolicyTest {
    @Test
    void usesStableDirectionOrderIncludingUnculledQuads() {
        assertEquals(Arrays.asList(
                Direction.DOWN, Direction.UP, Direction.NORTH,
                Direction.SOUTH, Direction.WEST, Direction.EAST, null),
                BlockQuadPolicy.directions());
    }

    @Test
    void resetsRandomSeedBeforeEveryDirection() {
        List<Integer> firstValues = new ArrayList<>();

        BlockQuadPolicy.forEachDirection(12345L,
                (direction, random) -> firstValues.add(random.nextInt()));

        assertEquals(7, firstValues.size());
        assertTrue(firstValues.stream().allMatch(firstValues.getFirst()::equals));
    }

    @Test
    void keepsBoundaryFacesAndDelegatesOnlyInsideLoadedSelection() {
        assertTrue(BlockQuadPolicy.shouldRenderFace(false, true, () -> false));
        assertTrue(BlockQuadPolicy.shouldRenderFace(true, false, () -> false));
        assertFalse(BlockQuadPolicy.shouldRenderFace(true, true, () -> false));
        assertTrue(BlockQuadPolicy.shouldRenderFace(true, true, () -> true));
    }
}
