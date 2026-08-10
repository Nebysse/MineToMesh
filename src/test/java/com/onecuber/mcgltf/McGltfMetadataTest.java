package com.onecuber.mcgltf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class McGltfMetadataTest {
    @Test
    void exposesStableModIdentity() {
        assertEquals("mcgltf", McGltf.MOD_ID);
        assertEquals("MC glTF Exporter", McGltf.DISPLAY_NAME);
        assertEquals("0.1.0", McGltf.VERSION);
    }
}
