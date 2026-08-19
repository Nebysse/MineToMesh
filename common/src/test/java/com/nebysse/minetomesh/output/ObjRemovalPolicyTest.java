package com.nebysse.minetomesh.output;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ObjRemovalPolicyTest {
    @Test
    void currentSourceAndOutputContractsContainNoObjWriter() throws Exception {
        Path root = projectRoot();
        assertFalse(Files.exists(root.resolve(
                "src/main/java/com/nebysse/minetomesh/obj")));
        String scene = Files.readString(root.resolve(
                "src/main/java/com/nebysse/minetomesh/output/StreamingSceneSession.java"),
                StandardCharsets.UTF_8);
        assertFalse(scene.contains("StreamingObjSession"));
        assertFalse(scene.contains(".obj"));
        assertFalse(scene.contains(".mtl"));
    }

    private static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("common/build.gradle"))) {
                return current.resolve("common");
            }
            if (current.getFileName() != null
                    && current.getFileName().toString().equals("common")
                    && Files.isRegularFile(current.resolve("build.gradle"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new AssertionError("Could not locate the common project");
    }
}
