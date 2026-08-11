package com.onecuber.mcgltf.wand;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExportWandMenuTest {
    @Test
    void validityDelegatesToTheFullHandSlotAndUuidBinding() throws Exception {
        String source = Files.readString(projectRoot().resolve(
                "src/main/java/com/onecuber/mcgltf/wand/ExportWandMenu.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("return binding.resolve(player).isPresent()"));
        assertTrue(source.contains("return binding.resolve(player)"));
        assertTrue(source.contains("return ItemStack.EMPTY"));
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
