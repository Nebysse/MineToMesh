package com.nebysse.minetomesh.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ServerExportSessionsTest {
    @Test
    void singletonWiresFabricLifecycleNetworkAndTracking() throws Exception {
        String source = read("src/main/java/com/nebysse/minetomesh/server/ServerExportSessions.java");
        assertTrue(source.contains("ServerTickEvents.END_SERVER_TICK"));
        assertTrue(source.contains("ServerPlayConnectionEvents.DISCONNECT"));
        assertTrue(source.contains("ServerLifecycleEvents.SERVER_STARTED"));
        assertTrue(source.contains("ServerLifecycleEvents.SERVER_STOPPING"));
        assertTrue(source.contains("WandServerSessionReceiver.install"));
        assertTrue(source.contains("installExportStarter"));
        assertTrue(source.contains("trackingCenter"));
        assertTrue(source.contains("recoverOnStartup"));
        assertFalse(source.contains("net.minecraft.client"));
    }

    @Test
    void metadataRegistersTheTrackingCenterMixin() throws Exception {
        String mixin = read("src/main/java/com/nebysse/minetomesh/mixin/ChunkMapTrackingCenterMixin.java");
        assertTrue(mixin.contains("method = \"updateChunkTracking\""));
        assertTrue(mixin.contains("ServerPlayer;chunkPosition()"));
        assertTrue(mixin.contains("ServerExportSessions.trackingCenter"));
        assertTrue(read("src/main/resources/minetomesh.mixins.json")
                .contains("ChunkMapTrackingCenterMixin"));
        assertTrue(read("src/main/resources/fabric.mod.json")
                .contains("minetomesh.mixins.json"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(moduleRoot().resolve(relative), StandardCharsets.UTF_8);
    }

    private static Path moduleRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("fabric-26.2/build.gradle"))) return current.resolve("fabric-26.2");
            if (current.getFileName() != null && current.getFileName().toString().equals("fabric-26.2")) return current;
            current = current.getParent();
        }
        throw new AssertionError("Fabric module not found");
    }
}
