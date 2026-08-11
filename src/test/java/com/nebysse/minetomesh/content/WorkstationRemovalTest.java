package com.nebysse.minetomesh.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkstationRemovalTest {
    @Test
    void workstationCodeRegistrationsAndResourcesAreAbsent() throws Exception {
        Path root = projectRoot();
        assertFalse(Files.exists(root.resolve(
                "src/main/java/com/nebysse/minetomesh/workstation")));
        assertFalse(Files.exists(root.resolve(
                "src/main/java/com/nebysse/minetomesh/client/workstation")));
        assertFalse(Files.exists(root.resolve(
                "src/main/resources/assets/minetomesh/blockstates/export_workstation.json")));
        assertFalse(Files.exists(root.resolve(
                "src/main/resources/data/minetomesh/recipe/export_workstation.json")));
        String content = Files.readString(root.resolve(
                "src/main/java/com/nebysse/minetomesh/content/MineToMeshContent.java"),
                StandardCharsets.UTF_8);
        assertFalse(content.contains("EXPORT_WORKSTATION"));
        try (var files = Files.walk(root.resolve(
                "src/main/java/com/nebysse/minetomesh/client/wand"))) {
            assertTrue(files.filter(Files::isRegularFile)
                    .noneMatch(path -> path.getFileName().toString()
                            .contains("Workstation")));
        }
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
