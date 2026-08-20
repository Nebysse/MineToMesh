package com.nebysse.minetomesh.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WandMenuMutationIntegrationTest {
    @Test
    void menuPayloadsResolveTheBoundWandBeforeMutation() throws Exception {
        String source = Files.readString(moduleRoot().resolve(
                "src/main/java/com/nebysse/minetomesh/network/WandPayloads.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("player.containerMenu instanceof ExportWandMenu"));
        assertTrue(source.contains("menu.resolveBoundStack(player)"));
        assertTrue(source.contains("setEndpoint"));
        assertTrue(source.contains("setOverlayEnabled"));
        assertTrue(source.contains("UpdateWandBatchSizePayload"));
        assertTrue(source.contains("setBatchChunkCount"));
        assertTrue(source.contains("payload.wandId().equals(menu.binding().wandId())"));
        assertTrue(source.contains("IllegalArgumentException ignored"));
        assertTrue(source.contains("ExportName.parse"));
        assertTrue(source.contains("setExportName"));
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
