package com.nebysse.minetomesh.wand;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExportWandMenuTest {
    @Test
    void validityDelegatesToTheFullHandSlotAndUuidBinding() throws Exception {
        String source = Files.readString(projectRoot().resolve(
                "src/main/java/com/nebysse/minetomesh/wand/ExportWandMenu.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("return binding.resolve(player).isPresent()"));
        assertTrue(source.contains("return binding.resolve(player)"));
        assertTrue(source.contains("return ItemStack.EMPTY"));
    }

    private static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("fabric-26.2/build.gradle"))) {
                return current.resolve("fabric-26.2");
            }
            current = current.getParent();
        }
        throw new AssertionError("Could not locate the Fabric module");
    }
}
