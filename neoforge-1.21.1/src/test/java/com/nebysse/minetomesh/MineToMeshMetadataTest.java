package com.nebysse.minetomesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MineToMeshMetadataTest {
    @Test
    void exposesMineToMeshReleaseIdentity() {
        assertEquals("minetomesh", MineToMesh.MOD_ID);
        assertEquals("MineToMesh", MineToMesh.DISPLAY_NAME);
        assertEquals("1.2.0", MineToMesh.VERSION);
    }

    @Test
    void metadataRequiresBothSides() throws Exception {
        String metadata;
        try (var input = MineToMeshMetadataTest.class.getResourceAsStream(
                "/META-INF/neoforge.mods.toml")) {
            metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(metadata.contains("version=\"1.2.0\""));
        assertTrue(metadata.contains("authors=\"岚苍穹 nebysse\""));
        assertFalse(metadata.contains("authors=\"OneCuber\""));
        assertFalse(metadata.contains("side=\"CLIENT\""));
        assertTrue(metadata.contains("item-owned export wand"));
    }
}
