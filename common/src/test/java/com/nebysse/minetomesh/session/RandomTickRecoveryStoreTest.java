package com.nebysse.minetomesh.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RandomTickRecoveryStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void atomicallyRoundTripsAndDeletesRecoveryRecord() throws Exception {
        Path path = temporaryDirectory.resolve("config/minetomesh/export-session-recovery.json");
        RandomTickRecoveryStore store = new RandomTickRecoveryStore(path);
        ServerExportSessionCoordinator.RecoveryRecord record =
                new ServerExportSessionCoordinator.RecoveryRecord(
                        UUID.randomUUID(), UUID.randomUUID(), "minecraft:overworld",
                        3, Instant.parse("2026-08-20T00:00:00Z"));

        store.write(record);
        RandomTickRecoveryStore.ReadResult result = store.read();
        assertEquals(RandomTickRecoveryStore.ReadStatus.VALID, result.status());
        assertEquals(record, result.record().orElseThrow());
        assertFalse(Files.exists(path.resolveSibling(path.getFileName() + ".tmp")));

        store.delete();
        assertEquals(RandomTickRecoveryStore.ReadStatus.MISSING, store.read().status());
    }

    @Test
    void corruptJournalIsReportedAndPreserved() throws Exception {
        Path path = temporaryDirectory.resolve("recovery.json");
        Files.writeString(path, "{broken", StandardCharsets.UTF_8);
        RandomTickRecoveryStore.ReadResult result =
                new RandomTickRecoveryStore(path).read();

        assertEquals(RandomTickRecoveryStore.ReadStatus.CORRUPT, result.status());
        assertTrue(result.error().isPresent());
        assertTrue(Files.exists(path));
    }
}
