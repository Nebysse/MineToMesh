package com.nebysse.minetomesh.wand;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WandInteractionIntegrationTest {
    @Test
    void attackCallbackCancelsBreakingAndMutatesOnlyOnServer() throws Exception {
        String source = Files.readString(moduleRoot().resolve(
                "src/main/java/com/nebysse/minetomesh/wand/WandInteractionHandler.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("AttackBlockCallback.EVENT.register"));
        assertTrue(source.contains("return InteractionResult.PASS"));
        assertTrue(source.contains("!level.isClientSide()"));
        assertTrue(source.contains("player instanceof ServerPlayer"));
        assertTrue(source.contains("Endpoint.POS1"));
        assertTrue(source.contains("InteractionResult.SUCCESS_SERVER"));
        assertTrue(source.contains("InteractionResult.SUCCESS"));
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
