package com.nebysse.minetomesh.client.selection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public final class LockedSelectionStore {
    private static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path file;
    private final FileReplacer replacer;
    private Map<WorldProfileKey, LockedSelection> records;

    private LockedSelectionStore(
            Path file,
            FileReplacer replacer,
            Map<WorldProfileKey, LockedSelection> records) {
        this.file = file;
        this.replacer = replacer;
        this.records = Map.copyOf(records);
    }

    public static LockedSelectionStore open(Path file) throws IOException {
        return open(file, LockedSelectionStore::replaceAtomically);
    }

    public static LockedSelectionStore empty(Path file) {
        return new LockedSelectionStore(normalize(file),
                LockedSelectionStore::replaceAtomically, Map.of());
    }

    static LockedSelectionStore open(Path file, FileReplacer replacer) throws IOException {
        Path normalized = normalize(file);
        Objects.requireNonNull(replacer, "replacer");
        if (!Files.exists(normalized)) {
            return new LockedSelectionStore(normalized, replacer, Map.of());
        }
        try {
            return new LockedSelectionStore(normalized, replacer, load(normalized));
        } catch (RuntimeException malformed) {
            quarantine(normalized);
            return new LockedSelectionStore(normalized, replacer, Map.of());
        }
    }

    public Optional<LockedSelection> get(WorldProfileKey profile) {
        return Optional.ofNullable(records.get(Objects.requireNonNull(profile, "profile")));
    }

    public boolean matches(WorldProfileKey profile, LockedSelection selection) {
        Objects.requireNonNull(selection, "selection");
        return selection.equals(records.get(Objects.requireNonNull(profile, "profile")));
    }

    public void put(WorldProfileKey profile, LockedSelection selection) throws IOException {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(selection, "selection");
        Map<WorldProfileKey, LockedSelection> updated = new LinkedHashMap<>(records);
        updated.put(profile, selection);
        persist(updated);
    }

    public void remove(WorldProfileKey profile) throws IOException {
        Objects.requireNonNull(profile, "profile");
        if (!records.containsKey(profile)) {
            return;
        }
        Map<WorldProfileKey, LockedSelection> updated = new LinkedHashMap<>(records);
        updated.remove(profile);
        persist(updated);
    }

    private void persist(Map<WorldProfileKey, LockedSelection> updated) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            Files.writeString(temporary, serialize(updated), StandardCharsets.UTF_8);
            replacer.replace(temporary, file);
            records = Map.copyOf(updated);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Map<WorldProfileKey, LockedSelection> load(Path file) throws IOException {
        JsonElement parsed;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            parsed = JsonParser.parseReader(reader);
        }
        JsonObject root = object(parsed, "root");
        int schema = exactInt(required(root, "schemaVersion"), "schemaVersion");
        if (schema != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported locked selection schema " + schema);
        }
        JsonObject profiles = object(required(root, "profiles"), "profiles");
        Map<WorldProfileKey, LockedSelection> loaded = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : profiles.entrySet()) {
            WorldProfileKey profile = new WorldProfileKey(entry.getKey());
            JsonObject value = object(entry.getValue(), "profile " + entry.getKey());
            String dimensionText = required(value, "dimension").getAsString();
            ResourceLocation dimension = ResourceLocation.parse(dimensionText);
            BlockPos pos1 = blockPos(required(value, "pos1"), "pos1");
            BlockPos pos2 = blockPos(required(value, "pos2"), "pos2");
            loaded.put(profile, new LockedSelection(dimension, pos1, pos2));
        }
        return loaded;
    }

    private static String serialize(Map<WorldProfileKey, LockedSelection> values) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        JsonObject profiles = new JsonObject();
        TreeMap<String, LockedSelection> sorted = new TreeMap<>();
        values.forEach((key, value) -> sorted.put(key.value(), value));
        sorted.forEach((key, value) -> {
            JsonObject record = new JsonObject();
            record.addProperty("dimension", value.dimension().toString());
            record.add("pos1", blockPos(value.pos1()));
            record.add("pos2", blockPos(value.pos2()));
            profiles.add(key, record);
        });
        root.add("profiles", profiles);
        return GSON.toJson(root) + System.lineSeparator();
    }

    private static JsonArray blockPos(BlockPos position) {
        JsonArray array = new JsonArray();
        array.add(position.getX());
        array.add(position.getY());
        array.add(position.getZ());
        return array;
    }

    private static BlockPos blockPos(JsonElement element, String field) {
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        JsonArray values = element.getAsJsonArray();
        if (values.size() != 3) {
            throw new IllegalArgumentException(field + " must contain exactly three integers");
        }
        return new BlockPos(exactInt(values.get(0), field),
                exactInt(values.get(1), field), exactInt(values.get(2), field));
    }

    private static int exactInt(JsonElement element, String field) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must contain integers");
        }
        try {
            return new BigDecimal(element.getAsString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must contain 32-bit integers", exception);
        }
    }

    private static JsonElement required(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("Missing " + field);
        }
        return value;
    }

    private static JsonObject object(JsonElement element, String field) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void quarantine(Path file) throws IOException {
        String suffix = ".corrupt-" + System.currentTimeMillis();
        Path target = file.resolveSibling(file.getFileName() + suffix);
        if (Files.exists(target)) {
            target = file.resolveSibling(file.getFileName() + suffix + "-" + UUID.randomUUID());
        }
        Files.move(file, target);
    }

    private static Path normalize(Path file) {
        return Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }
}

@FunctionalInterface
interface FileReplacer {
    void replace(Path source, Path target) throws IOException;
}
