package com.nebysse.minetomesh.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nebysse.minetomesh.output.ExportName;
import com.nebysse.minetomesh.output.OutputTransaction;
import com.nebysse.minetomesh.texture.TextureRegistry;
import com.nebysse.minetomesh.world.BlockPoint;
import com.nebysse.minetomesh.world.ExportPlan;
import com.nebysse.minetomesh.world.Selection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StreamingBatchSinkTest {
    @TempDir
    Path tempDir;

    @Test
    void finishesAnEmptySceneUsingPlatformSuppliedGameTime() throws Exception {
        ExportName name = ExportName.parse("empty");
        Selection selection = Selection.of(
                new BlockPoint("minecraft:overworld", 0, 0, 0),
                new BlockPoint("minecraft:overworld", 0, 0, 0));
        ExportPlan plan = new ExportPlan(selection, List.of(), List.of());
        StreamingBatchSink sink = new StreamingBatchSink(
                OutputTransaction.begin(tempDir, name),
                new TextureRegistry(),
                name,
                plan,
                Map.of("loader", "test"),
                10L,
                () -> 20L,
                new ExportTelemetry());

        assertTrue(sink.finishInput());
        Optional<ExportJob.WriterResult> result = Optional.empty();
        for (int attempt = 0; attempt < 200 && result.isEmpty(); attempt++) {
            Thread.sleep(10L);
            result = sink.pollResult();
        }

        assertTrue(result.isPresent());
        assertTrue(result.orElseThrow().success());
        Path output = result.orElseThrow().outputDirectory().orElseThrow();
        assertTrue(Files.isRegularFile(output.resolve("empty.gltf")));
        assertTrue(Files.isRegularFile(output.resolve("empty.usda")));
        assertEquals("completed", result.orElseThrow().status());
    }
}
