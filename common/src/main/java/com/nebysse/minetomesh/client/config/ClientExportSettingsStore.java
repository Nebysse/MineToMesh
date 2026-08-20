package com.nebysse.minetomesh.client.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Objects;

public final class ClientExportSettingsStore {
    public static final String FILE_NAME = "client-export-settings.json";

    private final Path path;
    private final int availableProcessors;

    public ClientExportSettingsStore(Path configDirectory, int availableProcessors) {
        Objects.requireNonNull(configDirectory, "configDirectory");
        this.availableProcessors = Math.max(1, availableProcessors);
        this.path = configDirectory.toAbsolutePath().normalize()
                .resolve(FILE_NAME);
    }

    public ClientExportSettings load() {
        if (Files.notExists(path)) {
            return ClientExportSettings.defaults(availableProcessors);
        }
        try {
            JsonObject json = JsonParser.parseString(
                    Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            int requested = json.get("worker_threads").getAsInt();
            return ClientExportSettings.clamped(requested, availableProcessors);
        } catch (Exception exception) {
            quarantine();
            return ClientExportSettings.defaults(availableProcessors);
        }
    }

    public void save(ClientExportSettings settings) throws IOException {
        Objects.requireNonNull(settings, "settings");
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        JsonObject json = new JsonObject();
        json.addProperty("worker_threads", settings.workerThreads());
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public Path path() {
        return path;
    }

    private void quarantine() {
        try {
            String suffix = ".corrupt-" + Instant.now().toEpochMilli();
            Files.move(path, path.resolveSibling(path.getFileName() + suffix),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // The corrupted file stays in place; defaults protect the client.
        }
    }
}
