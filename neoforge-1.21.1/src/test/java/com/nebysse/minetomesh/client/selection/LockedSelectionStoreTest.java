package com.nebysse.minetomesh.client.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LockedSelectionStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsOneSelectionPerProfileAndReloads() throws Exception {
        Path file = tempDir.resolve("locked-selections.json");
        LockedSelectionStore store = LockedSelectionStore.open(file);
        store.put(profile("a"), selection("minecraft:overworld", 0));
        store.put(profile("b"), selection("minecraft:the_nether", 20));

        LockedSelectionStore reloaded = LockedSelectionStore.open(file);
        assertEquals(selection("minecraft:overworld", 0),
                reloaded.get(profile("a")).orElseThrow());
        assertEquals(selection("minecraft:the_nether", 20),
                reloaded.get(profile("b")).orElseThrow());
        assertTrue(Files.readString(file).contains("\"schemaVersion\": 1"));
    }

    @Test
    void writesProfilesInStableHashOrder() throws Exception {
        Path file = tempDir.resolve("locked-selections.json");
        LockedSelectionStore store = LockedSelectionStore.open(file);
        WorldProfileKey first = profile("z-server");
        WorldProfileKey second = profile("a-server");
        store.put(first, selection("minecraft:overworld", 0));
        store.put(second, selection("minecraft:overworld", 10));

        String json = Files.readString(file);
        List<String> keys = List.of(first.value(), second.value()).stream().sorted().toList();
        assertTrue(json.indexOf(keys.get(0)) < json.indexOf(keys.get(1)));
    }

    @Test
    void failedReplacementKeepsOldMemoryAndFile() throws Exception {
        Path file = tempDir.resolve("locked-selections.json");
        LockedSelectionStore initial = LockedSelectionStore.open(file);
        initial.put(profile("a"), selection("minecraft:overworld", 0));
        LockedSelectionStore failing = LockedSelectionStore.open(
                file, (source, target) -> { throw new IOException("denied"); });

        assertThrows(IOException.class,
                () -> failing.put(profile("a"), selection("minecraft:overworld", 30)));
        assertEquals(selection("minecraft:overworld", 0),
                failing.get(profile("a")).orElseThrow());
        assertEquals(selection("minecraft:overworld", 0),
                LockedSelectionStore.open(file).get(profile("a")).orElseThrow());
        try (var files = Files.list(tempDir)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().contains(".tmp-")));
        }
    }

    @Test
    void removePersistsOnlyTheTargetProfile() throws Exception {
        Path file = tempDir.resolve("locked-selections.json");
        LockedSelectionStore store = LockedSelectionStore.open(file);
        store.put(profile("a"), selection("minecraft:overworld", 0));
        store.put(profile("b"), selection("minecraft:the_nether", 20));
        store.remove(profile("a"));

        LockedSelectionStore reloaded = LockedSelectionStore.open(file);
        assertTrue(reloaded.get(profile("a")).isEmpty());
        assertTrue(reloaded.get(profile("b")).isPresent());
    }

    @Test
    void quarantinesCorruptJsonAndStartsEmpty() throws Exception {
        Path file = tempDir.resolve("locked-selections.json");
        Files.writeString(file, "{ definitely not json");

        LockedSelectionStore store = LockedSelectionStore.open(file);

        assertTrue(store.get(profile("a")).isEmpty());
        assertFalse(Files.exists(file));
        try (var files = Files.list(tempDir)) {
            assertEquals(1L, files.filter(path -> path.getFileName().toString()
                    .matches("locked-selections\\.json\\.corrupt-.*")).count());
        }
    }

    @Test
    void rejectsIncompleteOrUnsupportedSchemaAsCorrupt() throws Exception {
        Path file = tempDir.resolve("locked-selections.json");
        Files.writeString(file, "{\"schemaVersion\":2,\"profiles\":{}}");
        LockedSelectionStore store = LockedSelectionStore.open(file);
        assertTrue(store.get(profile("a")).isEmpty());
    }

    private static WorldProfileKey profile(String name) {
        return WorldProfileKey.multiplayer(name + ".example:25565");
    }

    private static LockedSelection selection(String dimension, int offset) {
        return new LockedSelection(ResourceLocation.parse(dimension),
                new BlockPos(offset, 64, offset + 1),
                new BlockPos(offset + 4, 70, offset + 6));
    }
}
