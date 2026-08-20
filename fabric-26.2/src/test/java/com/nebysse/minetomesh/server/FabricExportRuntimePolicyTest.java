package com.nebysse.minetomesh.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FabricExportRuntimePolicyTest {
    @Test
    void runtimeUsesTransientLoadingTicketsAndTheirReturnedFutures() throws Exception {
        String source = read("src/main/java/com/nebysse/minetomesh/server/PlatformExportRuntime.java");
        assertTrue(source.contains("new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING)"));
        assertTrue(source.contains("addTicketAndLoadWithRadius(MINETOMESH_TICKET, pos, 0)"));
        assertTrue(source.contains("removeTicketWithRadius(MINETOMESH_TICKET, pos, 0)"));
        assertTrue(source.contains("CompletableFuture.allOf"));
        assertFalse(source.contains("updateChunkForced"));
        assertFalse(source.contains("net.minecraft.client"));
    }

    @Test
    void runtimeUsesTypedGameRuleAndCommonRecoveryStore() throws Exception {
        String source = read("src/main/java/com/nebysse/minetomesh/server/PlatformExportRuntime.java");
        assertTrue(source.contains("GameRules.RANDOM_TICK_SPEED"));
        assertTrue(source.contains("set(GameRules.RANDOM_TICK_SPEED, value, server)"));
        assertTrue(source.contains("config/minetomesh/export-session-recovery.json"));
        assertTrue(source.contains("RandomTickRecoveryStore"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(moduleRoot().resolve(relative), StandardCharsets.UTF_8);
    }

    private static Path moduleRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("fabric-26.2/build.gradle"))) return current.resolve("fabric-26.2");
            if (current.getFileName() != null && current.getFileName().toString().equals("fabric-26.2")) return current;
            current = current.getParent();
        }
        throw new AssertionError("Fabric module not found");
    }
}
