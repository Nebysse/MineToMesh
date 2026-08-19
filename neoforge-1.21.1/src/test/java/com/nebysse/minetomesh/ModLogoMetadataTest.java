package com.nebysse.minetomesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ModLogoMetadataTest {
    @Test
    void metadataReferencesScaledRootLogo() throws Exception {
        String metadata;
        try (InputStream input = ModLogoMetadataTest.class.getResourceAsStream(
                "/META-INF/neoforge.mods.toml")) {
            assertNotNull(input);
            metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(metadata.contains("logoFile=\"minetomesh_logo.png\""));
        assertTrue(metadata.contains("logoBlur=true"));

        try (InputStream input = ModLogoMetadataTest.class.getResourceAsStream(
                "/minetomesh_logo.png")) {
            assertNotNull(input, "logo must be packaged at JAR root");
            BufferedImage logo = ImageIO.read(input);
            assertNotNull(logo, "logo must be a readable PNG");
            assertEquals(512, logo.getWidth());
            assertEquals(491, logo.getHeight());
        }
    }
}
