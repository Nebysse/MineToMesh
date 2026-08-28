package com.nebysse.minetomesh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class IdentityMigrationContractTest {
    private static final String LEGACY_ID = "mc" + "gltf";
    private static final String LEGACY_PACKAGE = "com.onecuber." + LEGACY_ID;

    @Test
    void buildIdentityDeclaresMineToMesh130() throws Exception {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(
                repositoryRoot().resolve("gradle.properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        assertEquals("minetomesh", properties.getProperty("mod_id"));
        assertEquals("MineToMesh", properties.getProperty("mod_name"));
        assertEquals("1.4.0", properties.getProperty("neoforge_mod_version"));
        assertEquals("1.5.0", properties.getProperty("neoforge_12110_mod_version"));
        assertEquals("com.nebysse.minetomesh", properties.getProperty("mod_group_id"));
        assertTrue(read("build.gradle").contains("MineToMesh-${version}-neoforge-1.21.10.jar"));
        assertTrue(Files.readString(repositoryRoot().resolve("settings.gradle"))
                .contains("rootProject.name = 'MineToMesh'"));
    }

    @Test
    void sourceAndResourceRootsUseOnlyNewNamespaces() {
        Path root = projectRoot();
        assertTrue(Files.isDirectory(root.resolve("src/main/java/com/nebysse/minetomesh")));
        assertTrue(Files.isDirectory(root.resolve("src/test/java/com/nebysse/minetomesh")));
        assertTrue(Files.isDirectory(root.resolve("src/testmod/java/com/nebysse/minetomesh")));
        assertTrue(Files.isDirectory(root.resolve("src/main/resources/assets/minetomesh")));
        assertTrue(Files.isDirectory(root.resolve("src/main/resources/data/minetomesh")));
        assertTrue(Files.isDirectory(root.resolve("src/testmod/resources/assets/minetomesh_test")));

        assertFalse(Files.exists(root.resolve("src/main/java/com/onecuber/" + LEGACY_ID)));
        assertFalse(Files.exists(root.resolve("src/test/java/com/onecuber/" + LEGACY_ID)));
        assertFalse(Files.exists(root.resolve("src/testmod/java/com/onecuber/" + LEGACY_ID)));
        assertFalse(Files.exists(root.resolve("src/main/resources/assets/" + LEGACY_ID)));
        assertFalse(Files.exists(root.resolve("src/main/resources/data/" + LEGACY_ID)));
    }

    @Test
    void activeRuntimeTreesContainNoLegacyIdentity() throws Exception {
        for (String relative : List.of(
                "src/main/java",
                "src/main/resources",
                "src/test/java",
                "src/testmod/java",
                "src/testmod/resources")) {
            try (var files = Files.walk(projectRoot().resolve(relative))) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(IdentityMigrationContractTest::isText).toList()) {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    assertFalse(text.contains(LEGACY_PACKAGE), file.toString());
                    assertFalse(text.contains(LEGACY_ID + ":"), file.toString());
                    assertFalse(text.contains(LEGACY_ID + "_test"), file.toString());
                    assertFalse(text.contains("/" + LEGACY_ID), file.toString());
                    assertFalse(text.contains(LEGACY_ID + "-exports"), file.toString());
                    assertFalse(text.contains(LEGACY_ID + ".serverSmoke"), file.toString());
                }
            }
        }
    }

    @Test
    void serviceProviderFileUsesTargetPackageName() {
        Path service = projectRoot().resolve(
                "src/testmod/resources/META-INF/services/"
                        + "com.nebysse.minetomesh.backend.RenderBackendAdapter");
        assertTrue(Files.isRegularFile(service));
        assertFalse(Files.exists(projectRoot().resolve(
                "src/testmod/resources/META-INF/services/" + LEGACY_PACKAGE
                        + ".backend.RenderBackendAdapter")));
    }

    private static boolean isText(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".java") || name.endsWith(".json")
                || name.endsWith(".toml") || name.endsWith(".properties")
                || path.toString().replace('\\', '/').contains("/META-INF/services/");
    }

    private static String read(String relative) throws Exception {
        return Files.readString(projectRoot().resolve(relative), StandardCharsets.UTF_8);
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }

    private static Path repositoryRoot() {
        return projectRoot().getParent();
    }
}
