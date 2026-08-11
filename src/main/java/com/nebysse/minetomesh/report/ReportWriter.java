package com.nebysse.minetomesh.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.nebysse.minetomesh.scene.BatchCounters;
import com.nebysse.minetomesh.scene.Diagnostic;
import com.nebysse.minetomesh.world.BlockPoint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ReportWriter {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private ReportWriter() {
    }

    public static Path write(Path transactionRoot, ExportReport report) throws IOException {
        Path root = Objects.requireNonNull(transactionRoot, "transactionRoot")
                .toAbsolutePath().normalize();
        Objects.requireNonNull(report, "report");
        if (!Files.isDirectory(root)) {
            throw new IOException("Transaction root does not exist: " + root);
        }
        Path output = root.resolve("report.json").normalize();
        if (!output.startsWith(root)) {
            throw new IOException("Report path escapes transaction root");
        }
        Files.writeString(output, GSON.toJson(toJson(report)), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return output;
    }

    private static JsonObject toJson(ExportReport report) {
        JsonObject json = new JsonObject();
        json.addProperty("schemaVersion", report.schemaVersion());
        json.addProperty("status", report.status());
        json.addProperty("snapshotMode", report.snapshotMode());
        json.addProperty("dimension", report.dimension());
        json.add("min", intArray(report.min()));
        json.add("max", intArray(report.max()));
        json.add("origin", intArray(report.origin()));
        json.addProperty("volume", report.volume());
        json.addProperty("startGameTime", report.startGameTime());
        json.addProperty("endGameTime", report.endGameTime());
        json.add("counters", counters(report.counters()));

        JsonArray missingChunks = new JsonArray();
        report.missingChunks().stream().sorted().forEach(chunk -> {
            JsonObject value = new JsonObject();
            value.addProperty("chunkX", chunk.chunkX());
            value.addProperty("chunkZ", chunk.chunkZ());
            missingChunks.add(value);
        });
        json.add("missingChunks", missingChunks);

        List<Diagnostic> diagnostics = new ArrayList<>(report.diagnostics());
        diagnostics.sort(diagnosticComparator());
        JsonArray diagnosticJson = new JsonArray();
        diagnostics.forEach(diagnostic -> diagnosticJson.add(diagnostic(diagnostic)));
        json.add("diagnostics", diagnosticJson);

        JsonObject timings = new JsonObject();
        report.timingsMillis().forEach(timings::addProperty);
        json.add("timingsMillis", timings);
        return json;
    }

    private static JsonObject counters(BatchCounters counters) {
        JsonObject json = new JsonObject();
        json.addProperty("scannedPositions", counters.scannedPositions());
        json.addProperty("renderedBlocks", counters.renderedBlocks());
        json.addProperty("renderedFluids", counters.renderedFluids());
        json.addProperty("blockEntities", counters.blockEntities());
        json.addProperty("entities", counters.entities());
        json.addProperty("materials", counters.materials());
        json.addProperty("textures", counters.textures());
        json.addProperty("triangles", counters.triangles());
        json.addProperty("placeholders", counters.placeholders());
        return json;
    }

    private static JsonObject diagnostic(Diagnostic diagnostic) {
        JsonObject json = new JsonObject();
        json.addProperty("severity", diagnostic.severity().name());
        json.addProperty("code", diagnostic.code());
        json.addProperty("objectId", diagnostic.objectId());
        if (diagnostic.position().isPresent()) {
            BlockPoint position = diagnostic.position().orElseThrow();
            JsonObject point = new JsonObject();
            point.addProperty("dimension", position.dimension());
            point.addProperty("x", position.x());
            point.addProperty("y", position.y());
            point.addProperty("z", position.z());
            json.add("position", point);
        } else {
            json.add("position", JsonNull.INSTANCE);
        }
        json.addProperty("rendererClass", diagnostic.rendererClass());
        json.addProperty("exceptionType", diagnostic.exceptionType());
        json.addProperty("message", diagnostic.message());
        return json;
    }

    private static Comparator<Diagnostic> diagnosticComparator() {
        Comparator<BlockPoint> points = Comparator.comparing(BlockPoint::dimension)
                .thenComparingInt(BlockPoint::x)
                .thenComparingInt(BlockPoint::y)
                .thenComparingInt(BlockPoint::z);
        return Comparator.comparing(Diagnostic::code)
                .thenComparing(Diagnostic::objectId)
                .thenComparing(diagnostic -> diagnostic.position().orElse(null),
                        Comparator.nullsLast(points));
    }

    private static JsonArray intArray(int[] values) {
        JsonArray array = new JsonArray();
        for (int value : values) {
            array.add(value);
        }
        return array;
    }
}
