package com.nebysse.minetomesh.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nebysse.minetomesh.MineToMeshInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class FabricMetadataTest {
    @Test
    void declaresFabric262RuntimeAndSplitEntrypoints() throws Exception {
        Path metadata = moduleRoot().resolve("src/main/resources/fabric.mod.json");
        JsonObject root = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
        JsonObject depends = root.getAsJsonObject("depends");

        assertEquals("minetomesh", root.get("id").getAsString());
        assertEquals("1.4.0", MineToMeshInfo.CORE_VERSION);
        assertEquals("1.4.0-fabric-alpha.1", MineToMeshFabric.VERSION);
        assertEquals("*", root.get("environment").getAsString());
        assertEquals(">=0.19.3", depends.get("fabricloader").getAsString());
        assertEquals("~26.2", depends.get("minecraft").getAsString());
        assertEquals(">=25", depends.get("java").getAsString());
        assertEquals("*", depends.get("fabric-api").getAsString());
        assertTrue(root.getAsJsonObject("entrypoints").has("main"));
        assertTrue(root.getAsJsonObject("entrypoints").has("client"));
        assertEquals("minetomesh_logo.png", root.get("icon").getAsString());
    }

    private static Path moduleRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("fabric-26.2/build.gradle"))) {
                return current.resolve("fabric-26.2");
            }
            if (current.getFileName() != null
                    && current.getFileName().toString().equals("fabric-26.2")
                    && Files.isRegularFile(current.resolve("build.gradle"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new AssertionError("Could not locate the Fabric module");
    }
}
