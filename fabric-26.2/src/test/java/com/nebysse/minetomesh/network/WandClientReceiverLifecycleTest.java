package com.nebysse.minetomesh.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.junit.jupiter.api.Test;

class WandClientReceiverLifecycleTest {
    @Test
    void receiverCannotBeResetBetweenWorldConnections() {
        assertThrows(NoSuchMethodException.class,
                () -> WandClientReceiver.class.getDeclaredMethod("reset"));
    }

    @Test
    void rollingSessionPayloadsUseThePersistentSessionHandler() {
        AtomicReference<CustomPacketPayload> received = new AtomicReference<>();
        WandClientReceiver.installSessionHandler(received::set);
        ExportSessionFinishedPayload payload = new ExportSessionFinishedPayload(
                UUID.randomUUID(), UUID.randomUUID(), "minecraft:overworld", "completed");
        WandClientReceiver.receiveSession(payload);
        assertEquals(payload, received.get());
    }

    @Test
    void clientEntrypointRegistersPersistentGrantAndRejectReceivers() throws Exception {
        String source = Files.readString(moduleRoot().resolve(
                "src/client/java/com/nebysse/minetomesh/fabric/client/"
                        + "MineToMeshFabricClient.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("ExportWandGrantedPayload.TYPE"));
        assertTrue(source.contains("ExportWandRejectedPayload.TYPE"));
        assertTrue(source.contains("WandClientReceiver.receive(payload)"));
        assertTrue(source.contains("ExportSessionAcceptedPayload.TYPE"));
        assertTrue(source.contains("ExportSessionFailedPayload.TYPE"));
        assertTrue(source.contains("WandClientReceiver.receiveSession(payload)"));
        assertFalse(source.contains("WandClientReceiver.reset()"));
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
