package com.nebysse.minetomesh;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CommonPlatformIsolationTest {
    private static final List<String> FORBIDDEN = List.of(
            "net.minecraft.", "net.neoforged.", "net.fabricmc.",
            "com.mojang.blaze3d.", "org.lwjgl.");

    @Test
    void commonSourcesDoNotImportPlatformApis() throws IOException {
        Path root = findCommonRoot().resolve("src/main/java");
        List<Path> sources;
        if (Files.isDirectory(root)) {
            try (var files = Files.walk(root)) {
                sources = files
                        .filter(path -> path.toString().endsWith(".java"))
                        .toList();
            }
        } else {
            sources = List.of();
        }
        assertTrue(sources.size() >= 50,
                () -> "Common extraction is incomplete: " + sources.size());
        List<Path> violations = sources.stream()
                .filter(path -> {
                    try {
                        String source = Files.readString(path);
                        return FORBIDDEN.stream().anyMatch(source::contains);
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .toList();
        assertTrue(violations.isEmpty(), () -> "Platform imports in common: " + violations);
    }

    private static Path findCommonRoot() {
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
