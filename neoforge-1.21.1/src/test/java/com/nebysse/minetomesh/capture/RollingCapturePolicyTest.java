package com.nebysse.minetomesh.capture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RollingCapturePolicyTest {
    @Test
    void batchPlannerRestrictsSectionsToAuthorizedReadableChunks() throws Exception {
        String source = read("src/main/java/com/nebysse/minetomesh/world/WorldPlanner.java");
        assertTrue(source.contains("planBatch"));
        assertTrue(source.contains("level.hasChunk(chunk.x(), chunk.z())"));
        assertTrue(source.contains("Batch chunk is not readable on the client"));
        assertTrue(source.contains("chunks.isEmpty()"));
    }

    @Test
    void entityCollectionFiltersByHorizontalChunkMembership() throws Exception {
        String source = read("src/main/java/com/nebysse/minetomesh/capture/EntityCapture.java");
        assertTrue(source.contains("collectInChunks"));
        assertTrue(source.contains("entity.chunkPosition()"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(projectRoot().resolve(relative), StandardCharsets.UTF_8);
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
