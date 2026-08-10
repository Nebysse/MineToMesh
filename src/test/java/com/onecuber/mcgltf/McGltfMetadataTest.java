package com.onecuber.mcgltf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class McGltfMetadataTest {
    @Test
    void exposesStableModIdentity() {
        assertEquals("mcgltf", McGltf.MOD_ID);
        assertEquals("MC glTF Exporter", McGltf.DISPLAY_NAME);
    }

    @Test
    void declaresReleaseVersionZeroPointTwoPointZero() throws Exception {
        assertEquals("0.2.0", McGltf.VERSION);
        String metadata;
        try (var input = McGltfMetadataTest.class.getResourceAsStream(
                "/META-INF/neoforge.mods.toml")) {
            metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(metadata.contains("version=\"0.2.0\""));
    }
}
