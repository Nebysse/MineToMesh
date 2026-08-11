package com.onecuber.mcgltf.client.wand;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SelectionOverlayRendererTest {
    @Test
    void rendererResolvesTheCurrentHandsAndPreservesFaceAndEdgePasses()
            throws Exception {
        String source = Files.readString(projectRoot().resolve(
                "src/main/java/com/onecuber/mcgltf/client/wand/SelectionOverlayRenderer.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("player.getMainHandItem()"));
        assertTrue(source.contains("player.getOffhandItem()"));
        assertTrue(source.contains("RenderType.debugQuads()"));
        assertTrue(source.contains("RenderType.lines()"));
        assertFalse(source.contains("ExportWorkstationBlockEntity"));
        assertFalse(source.contains("OverlayKey"));
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
