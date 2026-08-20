package com.nebysse.minetomesh.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

final class FabricJarContractTest {
    private static final List<String> REQUIRED = List.of(
            "fabric.mod.json",
            "com/nebysse/minetomesh/fabric/MineToMeshFabric.class",
            "com/nebysse/minetomesh/fabric/client/MineToMeshFabricClient.class",
            "com/nebysse/minetomesh/gltf/GltfDocumentBuilder.class",
            "com/nebysse/minetomesh/usd/StreamingUsdaSession.class",
            "assets/minetomesh/lang/zh_cn.json",
            "assets/minetomesh/items/export_wand.json",
            "assets/minetomesh/models/item/export_wand.json",
            "assets/minetomesh/textures/item/export_wand.png",
            "data/minetomesh/recipe/export_wand.json");

    @Test
    void finalJarContainsBothEntrypointsCommonCoreAndResources() throws Exception {
        try (ZipFile jar = new ZipFile(jarPath().toFile())) {
            for (String entry : REQUIRED) {
                assertNotNull(jar.getEntry(entry), "Missing JAR entry: " + entry);
            }
            long guiSlices = jar.stream()
                    .map(ZipEntry::getName)
                    .filter(name -> name.matches(
                            "assets/minetomesh/textures/gui/export_wand/gui_\\d{3}\\.png"))
                    .count();
            assertEquals(77, guiSlices, "Unexpected export-wand GUI slice count");
        }
    }

    @Test
    void dedicatedServerEntrypointClosureHasNoClientReferences() throws Exception {
        List<String> serverClasses = List.of(
                "com/nebysse/minetomesh/fabric/MineToMeshFabric.class",
                "com/nebysse/minetomesh/network/WandPayloads.class",
                "com/nebysse/minetomesh/content/MineToMeshContent.class");
        try (ZipFile jar = new ZipFile(jarPath().toFile())) {
            for (String name : serverClasses) {
                ZipEntry entry = jar.getEntry(name);
                assertNotNull(entry, "Missing server class: " + name);
                String constants = new String(
                        jar.getInputStream(entry).readAllBytes(),
                        StandardCharsets.ISO_8859_1);
                assertFalse(constants.contains("net/minecraft/client/"),
                        name + " links client Minecraft classes");
                assertFalse(constants.contains("api/client/networking"),
                        name + " links Fabric client networking");
            }
        }
    }

    private static Path jarPath() throws IOException {
        Path libs = moduleRoot().resolve("build/libs");
        try (var files = Files.list(libs)) {
            return files.filter(path -> path.getFileName().toString()
                            .matches("MineToMesh-1\\.2\\.0-fabric-alpha\\.1\\+mc26\\.2\\.jar"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Final Fabric JAR not found in " + libs));
        }
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
