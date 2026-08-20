package com.nebysse.minetomesh.client.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientExportSettingsStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void missingFileUsesMachineDefaultWorkers() {
        ClientExportSettingsStore store = new ClientExportSettingsStore(
                temporaryDirectory.resolve("config/minetomesh"), 8);
        assertEquals(4, store.load().workerThreads());
    }

    @Test
    void storedValueIsClampedByCpuPolicyOnLoad() throws Exception {
        ClientExportSettingsStore store = new ClientExportSettingsStore(
                temporaryDirectory.resolve("config/minetomesh"), 8);
        store.save(new ClientExportSettings(99));
        assertEquals(6, store.load().workerThreads());

        store.save(new ClientExportSettings(2));
        assertEquals(2, store.load().workerThreads());
        assertFalse(Files.exists(store.path().resolveSibling(
                store.path().getFileName() + ".tmp")));
    }

    @Test
    void malformedFileIsQuarantinedAndDefaultsAreUsed() throws Exception {
        Path directory = temporaryDirectory.resolve("config/minetomesh");
        Files.createDirectories(directory);
        Path file = directory.resolve(ClientExportSettingsStore.FILE_NAME);
        Files.writeString(file, "{broken", StandardCharsets.UTF_8);

        ClientExportSettings settings = new ClientExportSettingsStore(directory, 8).load();

        assertEquals(4, settings.workerThreads());
        assertFalse(Files.exists(file));
        try (Stream<Path> entries = Files.list(directory)) {
            assertTrue(entries.anyMatch(entry -> entry.getFileName().toString()
                    .startsWith("client-export-settings.json.corrupt-")));
        }
    }

    @Test
    void settingsComputeEffectiveWorkersAgainstBatchAndCpu() {
        ClientExportSettings settings = new ClientExportSettings(14);
        assertEquals(4, settings.effectiveWorkers(4, 32));
        assertEquals(2, settings.effectiveWorkers(16, 4));
    }
}
