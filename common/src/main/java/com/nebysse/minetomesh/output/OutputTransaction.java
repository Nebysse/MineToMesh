package com.nebysse.minetomesh.output;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.UUID;

public final class OutputTransaction implements AutoCloseable {
    private final Path exportRoot;
    private final ExportName exportName;
    private final Path temporaryDirectory;
    private boolean published;
    private boolean closed;
    private Path finalDirectory;

    private OutputTransaction(Path exportRoot, ExportName exportName, Path temporaryDirectory) {
        this.exportRoot = exportRoot;
        this.exportName = exportName;
        this.temporaryDirectory = temporaryDirectory;
    }

    public static OutputTransaction begin(Path exportRoot, ExportName exportName) throws IOException {
        Objects.requireNonNull(exportRoot, "exportRoot");
        Objects.requireNonNull(exportName, "exportName");
        Path normalizedRoot = exportRoot.toAbsolutePath().normalize();
        Files.createDirectories(normalizedRoot);
        while (true) {
            Path temporary = normalizedRoot.resolve(".tmp-" + UUID.randomUUID());
            try {
                Files.createDirectory(temporary);
                return new OutputTransaction(normalizedRoot, exportName, temporary);
            } catch (FileAlreadyExistsException ignored) {
                // UUID collision: generate another transaction directory.
            }
        }
    }

    public Path temporaryDirectory() {
        return temporaryDirectory;
    }

    public Path publish() throws IOException {
        requireOpen();
        if (published) {
            return finalDirectory;
        }
        for (int suffix = 1; ; suffix = Math.incrementExact(suffix)) {
            String directoryName = suffix == 1
                    ? exportName.value()
                    : exportName.value() + "-" + suffix;
            Path candidate = exportRoot.resolve(directoryName);
            if (Files.exists(candidate)) {
                continue;
            }
            try {
                moveWithoutReplacement(temporaryDirectory, candidate);
                published = true;
                finalDirectory = candidate;
                return candidate;
            } catch (FileAlreadyExistsException ignored) {
                // Another export owns this name; try the next deterministic suffix.
            } catch (AccessDeniedException exception) {
                if (!Files.exists(candidate)) {
                    throw exception;
                }
                // Windows reports an atomic target collision as AccessDeniedException.
            }
        }
    }

    private static void moveWithoutReplacement(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Output transaction is closed");
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (!published && Files.exists(temporaryDirectory)) {
            deleteTree(temporaryDirectory);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
