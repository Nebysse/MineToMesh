package com.nebysse.minetomesh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MultiloaderStructureContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void repositoryContainsAllThreeModules() {
        assertTrue(Files.isDirectory(ROOT.resolve("common")));
        assertTrue(Files.isDirectory(ROOT.resolve("neoforge-1.21.1")));
        assertTrue(Files.isDirectory(ROOT.resolve("fabric-26.2")));
    }
}
