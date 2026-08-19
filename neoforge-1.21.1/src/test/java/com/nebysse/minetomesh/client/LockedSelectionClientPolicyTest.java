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
        assertTrue(source.contains("wandController,\n                                lockedSelectionService"));
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
        int reload = source.indexOf("private void onRegisterReloadListeners(", logout);
        String body = source.substring(logout, reload);
        assertFalse(body.contains("lockedSelectionService"));
        assertFalse(body.contains("LockedSelectionStore"));
        assertFalse(body.contains("locked-selections.json"));
        assertFalse(source.contains("lockedSelectionStore.clear"));
    }

    private static String source() throws Exception {
        return Files.readString(projectRoot().resolve(
                "src/main/java/com/nebysse/minetomesh/client/MineToMeshClient.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
