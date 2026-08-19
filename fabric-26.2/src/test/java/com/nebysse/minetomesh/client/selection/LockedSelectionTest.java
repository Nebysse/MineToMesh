package com.nebysse.minetomesh.client.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class LockedSelectionTest {
    @Test
    void convertsToNormalizedSelectionAndFiltersDimension() {
        LockedSelection locked = new LockedSelection(
                Identifier.parse("minecraft:overworld"),
                new BlockPos(4, 70, 8), new BlockPos(-2, 64, 1));

        assertEquals("minecraft:overworld", locked.toSelection().min().dimension());
        assertEquals(-2, locked.toSelection().min().x());
        assertTrue(locked.snapshot(
                Identifier.parse("minecraft:overworld")).isPresent());
        assertTrue(locked.snapshot(
                Identifier.parse("minecraft:the_nether")).isEmpty());
    }

    @Test
    void rejectsNullMembers() {
        Identifier dimension = Identifier.parse("minecraft:overworld");
        BlockPos point = BlockPos.ZERO;
        assertThrows(NullPointerException.class,
                () -> new LockedSelection(null, point, point));
        assertThrows(NullPointerException.class,
                () -> new LockedSelection(dimension, null, point));
        assertThrows(NullPointerException.class,
                () -> new LockedSelection(dimension, point, null));
    }
}
