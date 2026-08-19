package com.nebysse.minetomesh.client.wand;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SelectionOverlayRendererTest {
    @Test
    void rendererUses262SubmitNodesForFaceAndDepthTestedEdgePasses()
            throws Exception {
        String source = Files.readString(moduleRoot().resolve(
                "src/client/java/com/nebysse/minetomesh/client/wand/"
                        + "SelectionOverlayRenderer.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("heldSource.resolveSnapshot("));
        assertTrue(source.contains("player.getMainHandItem()"));
        assertTrue(source.contains("player.getOffhandItem()"));
        assertTrue(source.contains("lockedService.resolve("));
        assertTrue(source.contains("OverlaySnapshotPolicy.merge("));
        assertTrue(source.contains("LevelRenderEvents.COLLECT_SUBMITS.register"));
        assertTrue(source.contains("submitCustomGeometry("));
        assertTrue(source.contains("RenderTypes.debugQuads()"));
        assertTrue(source.contains("RenderTypes.lines()"));
        assertTrue(source.contains("RenderTypes.entityTranslucent(FORCEFIELD, false)"));
        assertFalse(source.contains("player.getInventory()"));
        assertFalse(source.contains("ExportWorkstationBlockEntity"));
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
