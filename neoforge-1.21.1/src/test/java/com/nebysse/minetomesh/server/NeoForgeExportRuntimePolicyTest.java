package com.nebysse.minetomesh.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NeoForgeExportRuntimePolicyTest {
    @Test
    void runtimeUsesSymmetricTransientTicketsAndNonblockingFullChunkFutures() throws Exception {
        String source = read("src/main/java/com/nebysse/minetomesh/server/PlatformExportRuntime.java");
        assertTrue(source.contains("TicketType.create"));
        assertTrue(source.contains("addRegionTicket(MINETOMESH_TICKET, pos, 2, key, false)"));
        assertTrue(source.contains("removeRegionTicket(MINETOMESH_TICKET, pos, 2, key, false)"));
        assertTrue(source.contains("ChunkStatus.FULL"));
        assertTrue(source.contains("getChunkFuture"));
        assertTrue(source.contains("CompletableFuture.allOf"));
        assertFalse(source.contains("updateChunkForced"));
        assertFalse(source.contains("net.minecraft.client"));
    }

    @Test
    void runtimeFreezesAndRestoresTheGlobalRuleThroughTheRecoveryJournal() throws Exception {
        String source = read("src/main/java/com/nebysse/minetomesh/server/PlatformExportRuntime.java");
        assertTrue(source.contains("GameRules.RULE_RANDOMTICKING"));
        assertTrue(source.contains("set(value, server)"));
        assertTrue(source.contains("config/minetomesh/export-session-recovery.json"));
        assertTrue(source.contains("RandomTickRecoveryStore"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(projectRoot().resolve(relative), StandardCharsets.UTF_8);
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
