package com.onecuber.mcgltf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class McGltfMetadataTest {
    @Test
    void exposesMineToMeshReleaseIdentity() {
        assertEquals("mcgltf", McGltf.MOD_ID);
        assertEquals("MineToMesh", McGltf.DISPLAY_NAME);
        assertEquals("0.3.2", McGltf.VERSION);
    }

    @Test
    void metadataRequiresBothSides() throws Exception {
        String metadata;
        try (var input = McGltfMetadataTest.class.getResourceAsStream(
                "/META-INF/neoforge.mods.toml")) {
            metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(metadata.contains("version=\"0.3.2\""));
        assertFalse(metadata.contains("side=\"CLIENT\""));
        assertTrue(metadata.contains("Client and server export workstation"));
    }
}
