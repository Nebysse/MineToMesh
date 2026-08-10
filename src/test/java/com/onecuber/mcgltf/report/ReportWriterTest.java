package com.onecuber.mcgltf.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.onecuber.mcgltf.scene.BatchCounters;
import com.onecuber.mcgltf.scene.Diagnostic;
import com.onecuber.mcgltf.world.BlockPoint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesDeterministicStructuredReport() throws Exception {
        ExportReport report = new ExportReport(
                "completed_with_warnings",
                "rolling",
                "minecraft:overworld",
                new int[] {0, 64, 0},
                new int[] {15, 79, 15},
                new int[] {0, 64, 0},
                4096L,
                100L,
                120L,
                new BatchCounters(4096, 10, 2, 1, 3, 4, 5, 6, 1),
                List.of(new ExportReport.MissingChunk(2, 1), new ExportReport.MissingChunk(-1, 4)),
                List.of(
                        diagnostic("Z_CODE", "b", 2),
                        diagnostic("A_CODE", "z", 3),
                        diagnostic("A_CODE", "a", 1)),
                Map.of("write", 8L, "capture", 12L));

        Path output = ReportWriter.write(tempDir, report);
        JsonObject json = JsonParser.parseString(Files.readString(output)).getAsJsonObject();

        assertEquals(1, json.get("schemaVersion").getAsInt());
        assertEquals("completed_with_warnings", json.get("status").getAsString());
        assertEquals(4096L, json.get("volume").getAsLong());
        assertEquals(10L, json.getAsJsonObject("counters").get("renderedBlocks").getAsLong());
        JsonArray missing = json.getAsJsonArray("missingChunks");
        assertEquals(-1, missing.get(0).getAsJsonObject().get("chunkX").getAsInt());
        assertEquals(2, missing.get(1).getAsJsonObject().get("chunkX").getAsInt());
        JsonArray diagnostics = json.getAsJsonArray("diagnostics");
        assertEquals("A_CODE", diagnostics.get(0).getAsJsonObject().get("code").getAsString());
        assertEquals("a", diagnostics.get(0).getAsJsonObject().get("objectId").getAsString());
        assertEquals("z", diagnostics.get(1).getAsJsonObject().get("objectId").getAsString());
        assertEquals("Z_CODE", diagnostics.get(2).getAsJsonObject().get("code").getAsString());
        assertEquals(12L, json.getAsJsonObject("timingsMillis").get("capture").getAsLong());
    }

    private static Diagnostic diagnostic(String code, String object, int x) {
        return new Diagnostic(Diagnostic.Severity.WARNING, code, object,
                Optional.of(new BlockPoint("minecraft:overworld", x, 64, 0)),
                "", "", "message");
    }
}
