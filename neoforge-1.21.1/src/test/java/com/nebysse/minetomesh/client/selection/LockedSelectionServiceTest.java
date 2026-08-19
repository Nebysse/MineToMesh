package com.nebysse.minetomesh.client.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LockedSelectionServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void togglesCurrentSelectionAndOverwritesDifferentSelection() throws Exception {
        WorldProfileKey profile = profile("server");
        LockedSelectionService service = serviceFor(profile);
        LockedSelection first = selection("minecraft:overworld", 0);
        LockedSelection second = selection("minecraft:overworld", 20);

        assertEquals(LockedSelectionService.ToggleResult.LOCKED,
                service.toggle(Optional.of(first)));
        assertTrue(service.isCurrent(first));
        assertEquals(LockedSelectionService.ToggleResult.REPLACED,
                service.toggle(Optional.of(second)));
        assertTrue(service.isCurrent(second));
        assertEquals(LockedSelectionService.ToggleResult.UNLOCKED,
                service.toggle(Optional.of(second)));
        assertTrue(service.resolve(
                ResourceLocation.parse("minecraft:overworld")).isEmpty());
    }

    @Test
    void rejectsIncompleteOrMissingProfileWithoutChangingStore() throws Exception {
        AtomicReference<Optional<WorldProfileKey>> current =
                new AtomicReference<>(Optional.of(profile("server")));
        LockedSelectionService service = new LockedSelectionService(
                LockedSelectionStore.open(tempDir.resolve("selection.json")), current::get);

        assertEquals(LockedSelectionService.ToggleResult.INCOMPLETE,
                service.toggle(Optional.empty()));
        current.set(Optional.empty());
        assertEquals(LockedSelectionService.ToggleResult.NO_PROFILE,
                service.toggle(Optional.of(selection("minecraft:overworld", 0))));
    }

    @Test
    void hidesAcrossDimensionsWithoutErasingTheRecord() throws Exception {
        LockedSelectionService service = serviceFor(profile("server"));
        LockedSelection locked = selection("minecraft:overworld", 0);
        service.toggle(Optional.of(locked));

        assertTrue(service.resolve(ResourceLocation.parse("minecraft:overworld")).isPresent());
        assertTrue(service.resolve(ResourceLocation.parse("minecraft:the_nether")).isEmpty());
        assertTrue(service.resolve(ResourceLocation.parse("minecraft:overworld")).isPresent());
        assertTrue(service.isCurrent(locked));
    }

    @Test
    void reportsWriteFailureAndRetainsPreviousState() throws Exception {
        Path file = tempDir.resolve("selection.json");
        WorldProfileKey profile = profile("server");
        LockedSelection oldSelection = selection("minecraft:overworld", 0);
        LockedSelectionStore initial = LockedSelectionStore.open(file);
        initial.put(profile, oldSelection);
        LockedSelectionStore failing = LockedSelectionStore.open(
                file, (source, target) -> { throw new IOException("denied"); });
        LockedSelectionService service = new LockedSelectionService(
                failing, () -> Optional.of(profile));

        assertEquals(LockedSelectionService.ToggleResult.WRITE_FAILED,
                service.toggle(Optional.of(selection("minecraft:overworld", 30))));
        assertTrue(service.lastError().orElseThrow().contains("denied"));
        assertTrue(service.isCurrent(oldSelection));
        assertFalse(service.isCurrent(selection("minecraft:overworld", 30)));
    }

    private LockedSelectionService serviceFor(WorldProfileKey profile) throws Exception {
        return new LockedSelectionService(
                LockedSelectionStore.open(tempDir.resolve("selection.json")),
                () -> Optional.of(profile));
    }

    private static WorldProfileKey profile(String name) {
        return WorldProfileKey.multiplayer(name + ".example:25565");
    }

    private static LockedSelection selection(String dimension, int offset) {
        return new LockedSelection(ResourceLocation.parse(dimension),
                new BlockPos(offset, 64, offset),
                new BlockPos(offset + 4, 70, offset + 4));
    }
}
