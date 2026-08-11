package com.onecuber.mcgltf.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WandPermissionIntegrationTest {
    @Test
    void exportRequestUsesBoundMenuSnapshotAndServerPermission() throws Exception {
        String source = Files.readString(projectRoot().resolve(
                "src/main/java/com/onecuber/mcgltf/network/WandPayloads.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains(
                "player.containerMenu instanceof ExportWandMenu menu"));
        assertTrue(source.contains("menu.resolveBoundStack(player)"));
        assertTrue(source.contains("player.getServer().isSingleplayer()"));
        assertTrue(source.contains(
                "player.createCommandSourceStack().hasPermission(2)"));
        assertTrue(source.contains("new ExportWandGrantedPayload("));
        assertTrue(source.contains("selection.pos1().orElseThrow()"));
        assertTrue(source.contains("selection.pos2().orElseThrow()"));
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
