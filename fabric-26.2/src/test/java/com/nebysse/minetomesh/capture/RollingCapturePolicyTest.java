package com.nebysse.minetomesh.capture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RollingCapturePolicyTest {
    @Test
    void batchPlannerRestrictsSectionsToAuthorizedReadableChunks() throws Exception {
        String source = read("src/client/java/com/nebysse/minetomesh/world/WorldPlanner.java");
        assertTrue(source.contains("planBatch"));
        assertTrue(source.contains("level.hasChunk(chunk.x(), chunk.z())"));
        assertTrue(source.contains("Batch chunk is not readable on the client"));
        assertTrue(source.contains("chunks.isEmpty()"));
    }

    @Test
    void entityCollectionFiltersByHorizontalChunkMembership() throws Exception {
        String source = read("src/client/java/com/nebysse/minetomesh/capture/EntityCapture.java");
        assertTrue(source.contains("collectInChunks"));
        assertTrue(source.contains("entity.chunkPosition()"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(moduleRoot().resolve(relative), StandardCharsets.UTF_8);
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
