package com.nebysse.minetomesh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MultiloaderStructureContractTest {
    @Test
    void repositoryContainsAllThreeModules() {
        Path root = findRepositoryRoot();
        assertTrue(Files.isDirectory(root.resolve("common")));
        assertTrue(Files.isDirectory(root.resolve("neoforge-1.21.1")));
        assertTrue(Files.isDirectory(root.resolve("fabric-26.2")));
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve("common"))
                    && Files.isDirectory(current.resolve("neoforge-1.21.1"))
                    && Files.isDirectory(current.resolve("fabric-26.2"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new AssertionError("Could not locate the multiloader repository root");
    }
}
