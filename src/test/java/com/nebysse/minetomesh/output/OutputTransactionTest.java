package com.nebysse.minetomesh.output;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nebysse.minetomesh.scene.BatchCounters;
import com.nebysse.minetomesh.scene.ChunkBatch;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutputTransactionTest {
    @TempDir
    Path tempDir;

    @Test
    void publishesWithoutReplacingExistingExportsAndUsesSuffixes() throws Exception {
        Path exports = tempDir.resolve("exports");
        Path firstPath;
        try (OutputTransaction first = OutputTransaction.begin(exports, ExportName.parse("castle"))) {
            assertTrue(first.temporaryDirectory().getFileName().toString().matches("\\.tmp-[0-9a-f-]{36}"));
            Files.writeString(first.temporaryDirectory().resolve("marker.txt"), "first");
            firstPath = first.publish();
        }
        Path secondPath;
        try (OutputTransaction second = OutputTransaction.begin(exports, ExportName.parse("castle"))) {
            Files.writeString(second.temporaryDirectory().resolve("marker.txt"), "second");
            secondPath = second.publish();
        }

        assertEquals(exports.resolve("castle"), firstPath);
        assertEquals(exports.resolve("castle-2"), secondPath);
        assertEquals("first", Files.readString(firstPath.resolve("marker.txt")));
        assertEquals("second", Files.readString(secondPath.resolve("marker.txt")));
    }

    @Test
    void publishesCompleteDualFormatLayout() throws Exception {
        Path exports = tempDir.resolve("exports");
        Path output;
        try (OutputTransaction transaction = OutputTransaction.begin(
                exports, ExportName.parse("sample"));
             StreamingSceneSession scene = new StreamingSceneSession(
                     transaction.temporaryDirectory(), "sample", Map.of())) {
            scene.append(emptyBatch());
            scene.finish();
            Files.writeString(transaction.temporaryDirectory().resolve("report.json"), "{}");
            output = transaction.publish();
        }

        assertAll(
                () -> assertTrue(Files.exists(output.resolve("sample.gltf"))),
                () -> assertTrue(Files.exists(output.resolve("sample.bin"))),
                () -> assertTrue(Files.exists(output.resolve("sample.usda"))),
                () -> assertFalse(Files.exists(output.resolve("sample.obj"))),
                () -> assertFalse(Files.exists(output.resolve("sample.mtl"))),
                () -> assertTrue(Files.exists(output.resolve("report.json"))));
    }

    @Test
    void usdaFinishFailurePublishesNothingAndRemovesTemporaryTree() throws Exception {
        Path exports = tempDir.resolve("exports");
        Path temporary;
        try (OutputTransaction transaction = OutputTransaction.begin(
                exports, ExportName.parse("sample"))) {
            temporary = transaction.temporaryDirectory();
            Files.writeString(temporary.resolve("sample.usda"), "collision");
            assertThrows(IOException.class, () -> {
                try (StreamingSceneSession scene = new StreamingSceneSession(
                        temporary, "sample", Map.of())) {
                    scene.append(emptyBatch());
                    scene.finish();
                }
            });
        }

        assertFalse(Files.exists(temporary));
        assertFalse(Files.exists(exports.resolve("sample")));
    }

    @Test
    void closeDeletesOnlyItsUnpublishedTemporaryTree() throws Exception {
        Path exports = tempDir.resolve("exports");
        Path existing = Files.createDirectories(exports.resolve("castle"));
        Files.writeString(existing.resolve("keep.txt"), "keep");
        Path temporary;

        try (OutputTransaction transaction = OutputTransaction.begin(exports, ExportName.parse("cancelled"))) {
            temporary = transaction.temporaryDirectory();
            Files.createDirectories(temporary.resolve("nested"));
            Files.writeString(temporary.resolve("nested/partial.bin"), "partial");
        }

        assertFalse(Files.exists(temporary));
        assertEquals("keep", Files.readString(existing.resolve("keep.txt")));
    }

    private static ChunkBatch emptyBatch() {
        return new ChunkBatch(List.of(), List.of(), BatchCounters.ZERO);
    }
}
