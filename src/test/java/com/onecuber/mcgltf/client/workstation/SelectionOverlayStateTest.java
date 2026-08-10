package com.onecuber.mcgltf.client.workstation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onecuber.mcgltf.workstation.WorkstationCoordinates;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelectionOverlayStateTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private final BlockPos station = new BlockPos(10, 64, 10);
    private final OverlayKey key = new OverlayKey(OVERWORLD, station);
    private final WorkstationCoordinates coordinates = new WorkstationCoordinates(
            new BlockPos(0, 64, 0), new BlockPos(10, 70, 10));
    private SelectionOverlayState state;

    @BeforeEach
    void setUp() {
        state = new SelectionOverlayState();
    }

    @Test
    void togglePersistsAfterScreenClose() {
        state.toggle(key, coordinates);
        state.screenClosed(key);
        assertTrue(state.visible(key));
    }

    @Test
    void toggleTwiceHides() {
        state.toggle(key, coordinates);
        state.toggle(key, coordinates);
        assertFalse(state.visible(key));
    }

    @Test
    void refreshUpdatesCoordinatesWithoutHiding() {
        state.toggle(key, coordinates);
        WorkstationCoordinates updated = new WorkstationCoordinates(
                new BlockPos(5, 5, 5), new BlockPos(9, 9, 9));
        state.refresh(key, updated);
        assertTrue(state.visible(key));
        assertEquals(updated, state.coordinates(key).orElseThrow());
    }

    @Test
    void removeClearsSingleStation() {
        state.toggle(key, coordinates);
        state.remove(key);
        assertFalse(state.visible(key));
    }

    @Test
    void dimensionChangeClearsOtherDimensions() {
        state.toggle(key, coordinates);
        OverlayKey other = new OverlayKey(NETHER, station);
        state.toggle(other, coordinates);
        state.dimensionChanged(OVERWORLD);
        assertTrue(state.visible(key));
        assertFalse(state.visible(other));
    }

    @Test
    void logoutClearsEverything() {
        state.toggle(key, coordinates);
        state.clear();
        assertFalse(state.visible(key));
    }
}
