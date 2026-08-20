package com.nebysse.minetomesh.session;

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
import java.util.Optional;
import java.util.UUID;

public final class RandomTickRecoveryStore {
    public enum ReadStatus {
        MISSING,
        VALID,
        CORRUPT
    }

    public record ReadResult(
            ReadStatus status,
            Optional<ServerExportSessionCoordinator.RecoveryRecord> record,
            Optional<String> error) {
        public ReadResult {
            Objects.requireNonNull(status, "status");
            record = Objects.requireNonNull(record, "record");
            error = Objects.requireNonNull(error, "error");
            if (status == ReadStatus.VALID && record.isEmpty()) {
                throw new IllegalArgumentException("Valid recovery result requires a record");
            }
            if (status == ReadStatus.CORRUPT && error.isEmpty()) {
                throw new IllegalArgumentException("Corrupt recovery result requires an error");
            }
        }

        static ReadResult missing() {
            return new ReadResult(ReadStatus.MISSING, Optional.empty(), Optional.empty());
        }

        static ReadResult valid(
                ServerExportSessionCoordinator.RecoveryRecord record) {
            return new ReadResult(ReadStatus.VALID, Optional.of(record), Optional.empty());
        }

        static ReadResult corrupt(Exception exception) {
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
            return new ReadResult(
                    ReadStatus.CORRUPT, Optional.empty(), Optional.of(message));
        }
    }

    private final Path path;

    public RandomTickRecoveryStore(Path path) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    public void write(
            ServerExportSessionCoordinator.RecoveryRecord record) throws IOException {
        Objects.requireNonNull(record, "record");
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = temporaryPath();
        byte[] bytes = encode(record).getBytes(StandardCharsets.UTF_8);
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

    public ReadResult read() {
        if (Files.notExists(path)) {
            return ReadResult.missing();
        }
        try {
            JsonObject json = JsonParser.parseString(
                    Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            return ReadResult.valid(new ServerExportSessionCoordinator.RecoveryRecord(
                    UUID.fromString(requiredString(json, "session_id")),
                    UUID.fromString(requiredString(json, "player_id")),
                    requiredString(json, "dimension"),
                    json.get("random_tick_speed").getAsInt(),
                    Instant.parse(requiredString(json, "created_at"))));
        } catch (Exception exception) {
            return ReadResult.corrupt(exception);
        }
    }

    public void delete() throws IOException {
        Files.deleteIfExists(path);
        Files.deleteIfExists(temporaryPath());
    }

    public Path path() {
        return path;
    }

    private Path temporaryPath() {
        return path.resolveSibling(path.getFileName() + ".tmp");
    }

    private static String encode(
            ServerExportSessionCoordinator.RecoveryRecord record) {
        JsonObject json = new JsonObject();
        json.addProperty("session_id", record.sessionId().toString());
        json.addProperty("player_id", record.playerId().toString());
        json.addProperty("dimension", record.dimension());
        json.addProperty("random_tick_speed", record.randomTickSpeed());
        json.addProperty("created_at", record.createdAt().toString());
        return json.toString();
    }

    private static String requiredString(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            throw new IllegalArgumentException("Missing recovery field: " + key);
        }
        return json.get(key).getAsString();
    }
}
