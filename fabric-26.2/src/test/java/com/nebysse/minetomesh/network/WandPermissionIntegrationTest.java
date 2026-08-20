package com.nebysse.minetomesh.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WandPermissionIntegrationTest {
    @Test
    void exportRequestUsesBoundMenuSnapshotAndServerPermission() throws Exception {
        String source = Files.readString(moduleRoot().resolve(
                "src/main/java/com/nebysse/minetomesh/network/WandPayloads.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains(
                "player.containerMenu instanceof ExportWandMenu menu"));
        assertTrue(source.contains("menu.resolveBoundStack(player)"));
        assertTrue(source.contains("context.server().isSingleplayer()"));
        assertTrue(source.contains("permissions()"));
        assertTrue(source.contains("Permissions.COMMANDS_GAMEMASTER"));
        assertTrue(source.contains("WandServerSessionReceiver.requestExport"));
        assertTrue(source.contains("player, wandId, selection, payload.exportName()"));
        assertTrue(source.contains("selection.batchChunkCount()")
                || source.contains("ExportRequest("));
    }

    private static Path moduleRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("fabric-26.2/build.gradle"))) {
                return current.resolve("fabric-26.2");
            }
            if (current.getFileName() != null
                    && current.getFileName().toString().equals("fabric-26.2")
                    && Files.isRegularFile(current.resolve("build.gradle"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new AssertionError("Could not locate the Fabric module");
    }
}
