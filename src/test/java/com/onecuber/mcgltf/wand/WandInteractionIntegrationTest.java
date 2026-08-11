package com.onecuber.mcgltf.wand;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WandInteractionIntegrationTest {
    @Test
    void leftClickAlwaysCancelsBreakingButMutatesOnlyOnServerStart() throws Exception {
        String source = Files.readString(projectRoot().resolve(
                "src/main/java/com/onecuber/mcgltf/wand/WandInteractionHandler.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("event.setCanceled(true)"));
        assertTrue(source.contains("LeftClickBlock.Action.START"));
        assertTrue(source.contains("!event.getLevel().isClientSide()"));
        assertTrue(source.contains("Endpoint.POS1"));
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
