package com.onecuber.mcgltf.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkstationPermissionIntegrationTest {
    @Test
    void exportHandlerChecksServerPermissionBeforeValidatingExportData() throws Exception {
        String source = Files.readString(projectRoot().resolve(
                "src/main/java/com/onecuber/mcgltf/network/WorkstationPayloads.java"),
                StandardCharsets.UTF_8);
        int exportHandler = source.indexOf("private static void handleExportRequest");
        int permission = source.indexOf("validateExportPermission", exportHandler);
        int exportName = source.indexOf("validateExportName", exportHandler);
        assertTrue(permission > exportHandler,
                "export handler must invoke the server-authoritative permission policy");
        assertTrue(exportName > permission,
                "permission must be rejected before validating export data");
        assertTrue(source.contains("player.getServer().isSingleplayer()"),
                "integrated singleplayer servers must bypass permission checks");
        assertTrue(source.contains("player.createCommandSourceStack().hasPermission(2)"),
                "dedicated servers must require command permission level two");
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
