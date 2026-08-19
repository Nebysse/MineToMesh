package com.nebysse.minetomesh.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LockedSelectionClientPolicyTest {
    @Test
    void onePersistentServiceIsSharedByRendererAndScreen() throws Exception {
        String source = source();
        assertTrue(source.contains("gameDirectory.toPath()"));
        assertTrue(source.contains(".resolve(\"config\").resolve(\"minetomesh\")"));
        assertTrue(source.contains(".resolve(\"locked-selections.json\")"));
        assertTrue(source.contains("lockedSelectionService = new LockedSelectionService("));
        assertTrue(source.contains("new HeldWandOverlaySource(), lockedSelectionService"));
        assertTrue(source.contains("wandController, lockedSelectionService"));
        assertTrue(source.contains("LockedSelectionStore.empty(lockFile)"));
    }

    @Test
    void profileResolutionUsesCurrentServerOrIntegratedWorldRoot() throws Exception {
        String source = source();
        assertTrue(source.contains("minecraft.getCurrentServer().ip"));
        assertTrue(source.contains("WorldProfileKey.multiplayer("));
        assertTrue(source.contains("minecraft.getSingleplayerServer()"));
        assertTrue(source.contains("getWorldPath(LevelResource.ROOT)"));
        assertTrue(source.contains("WorldProfileKey.singleplayer(worldRoot)"));
    }

    @Test
    void logoutAndDimensionChangesNeverDeletePersistentRecords() throws Exception {
        String source = source();
        int logout = source.indexOf("private void onLoggingOut(");
        String body = source.substring(logout);
        assertFalse(body.contains("lockedSelectionService"));
        assertFalse(body.contains("LockedSelectionStore"));
        assertFalse(body.contains("locked-selections.json"));
        assertFalse(source.contains("lockedSelectionStore.clear"));
    }

    private static String source() throws Exception {
        return Files.readString(moduleRoot().resolve(
                "src/client/java/com/nebysse/minetomesh/client/MineToMeshClient.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
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
