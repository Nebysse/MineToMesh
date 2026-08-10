package com.onecuber.mcgltf.client.workstation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SelectionOverlayIntegrationTest {
    @Test
    void rendererUsesStoredMenuCoordinatesAndMatchingPositionColorRenderType() throws Exception {
        String source = Files.readString(projectRoot().resolve(
                "src/main/java/com/onecuber/mcgltf/client/workstation/SelectionOverlayRenderer.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("RenderType.debugQuads()"),
                "overlay faces need the built-in POSITION_COLOR render type");
        assertTrue(source.contains("entry.getValue()"),
                "renderer must preserve coordinates captured from the synchronized menu");
        assertFalse(source.contains("RENDERTYPE_TRANSLUCENT_SHADER"),
                "block translucent shader is incompatible with POSITION_COLOR vertices");
    }

    @Test
    void workstationBlockEntitySynchronizesCoordinatesToClients() throws Exception {
        String source = Files.readString(projectRoot().resolve(
                "src/main/java/com/onecuber/mcgltf/workstation/ExportWorkstationBlockEntity.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("getUpdatePacket()"),
                "workstation block entity must emit update packets");
        assertTrue(source.contains("getUpdateTag("),
                "workstation block entity must expose coordinate update tags");
        assertTrue(source.contains("ClientboundBlockEntityDataPacket.create(this)"),
                "update packet must carry this block entity");
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
