package com.nebysse.minetomesh.capture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuxiliaryCoordinatePolicyTest {
    @Test
    void auxiliaryCapturePathsUseSharedRightHandedCoordinates() throws Exception {
        Map<String, String> sources = Map.of(
                "BlockEntityCapture", read("src/main/java/com/nebysse/minetomesh/capture/BlockEntityCapture.java"),
                "EntityCapture", read("src/main/java/com/nebysse/minetomesh/capture/EntityCapture.java"),
                "DefaultExportPipeline", read("src/main/java/com/nebysse/minetomesh/job/DefaultExportPipeline.java"));

        for (Map.Entry<String, String> entry : sources.entrySet()) {
            String source = entry.getValue();
            String owner = entry.getKey();
            assertTrue(source.contains("CaptureCoordinates"),
                    owner + " must use the shared coordinate contract");
            assertFalse(source.contains("scale(1.0F, 1.0F, -1.0F)"),
                    owner + " must not inject a reflected pose");
            assertFalse(source.contains("-(position.getZ() - selection.min().z())"),
                    owner + " must not reflect block-entity Z");
            assertFalse(source.contains("-(entity.getZ() - selection.min().z()"),
                    owner + " must not reflect entity Z");
            assertFalse(source.contains("new Vec3f(x, y, -z - 1.0F)"),
                    owner + " must not reflect block placeholder bounds");
        }
    }

    private static String read(String relative) throws Exception {
        return Files.readString(projectRoot().resolve(relative), StandardCharsets.UTF_8);
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
