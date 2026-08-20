package com.nebysse.minetomesh.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ServerExportSessionsTest {
    @Test
    void singletonWiresRequestsPacketsLifecycleAndTrackingOverrides() throws Exception {
        String source = read("src/main/java/com/nebysse/minetomesh/server/ServerExportSessions.java");
        assertTrue(source.contains("WandServerSessionReceiver.install"));
        assertTrue(source.contains("installExportStarter"));
        assertTrue(source.contains("ServerExportSessionCoordinator"));
        assertTrue(source.contains("trackingCenter"));
        assertTrue(source.contains("onServerTick"));
        assertTrue(source.contains("onPlayerLoggedOut"));
        assertTrue(source.contains("onPlayerChangedDimension"));
        assertTrue(source.contains("onServerStopping"));
        assertTrue(source.contains("recoverOnStartup"));
        assertFalse(source.contains("net.minecraft.client"));
    }

    @Test
    void mixinRedirectsOnlyTheVanillaTrackingCenterLookup() throws Exception {
        String source = read("src/main/java/com/nebysse/minetomesh/mixin/ChunkMapTrackingCenterMixin.java");
        assertTrue(source.contains("method = \"updateChunkTracking\""));
        assertTrue(source.contains("ServerPlayer;chunkPosition()"));
        assertTrue(source.contains("ServerExportSessions.trackingCenter"));
        String config = read("src/main/resources/minetomesh.mixins.json");
        assertTrue(config.contains("ChunkMapTrackingCenterMixin"));
        String metadata = read("src/main/templates/META-INF/neoforge.mods.toml");
        assertTrue(metadata.contains("minetomesh.mixins.json"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(projectRoot().resolve(relative), StandardCharsets.UTF_8);
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
