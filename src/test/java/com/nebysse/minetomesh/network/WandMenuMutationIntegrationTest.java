package com.nebysse.minetomesh.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WandMenuMutationIntegrationTest {
    @Test
    void menuPayloadsResolveTheBoundWandBeforeMutation() throws Exception {
        String source = Files.readString(projectRoot().resolve(
                "src/main/java/com/nebysse/minetomesh/network/WandPayloads.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("player.containerMenu instanceof ExportWandMenu"));
        assertTrue(source.contains("menu.resolveBoundStack(player)"));
        assertTrue(source.contains("setEndpoint"));
        assertTrue(source.contains("setOverlayEnabled"));
        assertTrue(source.contains("ExportName.parse"));
        assertTrue(source.contains("setExportName"));
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
