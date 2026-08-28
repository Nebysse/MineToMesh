package com.nebysse.minetomesh.client.wand;

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
                "src/main/java/com/nebysse/minetomesh/client/wand/SelectionOverlayRenderer.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("heldSource.resolveSnapshot("));
        assertTrue(source.contains("player.getMainHandItem()"));
        assertTrue(source.contains("player.getOffhandItem()"));
        assertTrue(source.contains("lockedService.resolve("));
        assertTrue(source.contains("OverlaySnapshotPolicy.merge("));
        assertTrue(source.contains("for (HeldWandOverlaySource.Snapshot snapshot : snapshots)"));
        assertTrue(source.contains("RenderType.debugQuads()"));
        assertTrue(source.contains("RenderType.lines()"));
        assertFalse(source.contains("player.getInventory()"));
        assertFalse(source.contains("ExportWorkstationBlockEntity"));
        assertFalse(source.contains("OverlayKey"));
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
