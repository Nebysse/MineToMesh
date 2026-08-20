package com.nebysse.minetomesh.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void loggingOutDoesNotEraseWandPayloadHandlers() throws Exception {
        String source = Files.readString(projectRoot().resolve(
                "src/main/java/com/nebysse/minetomesh/client/MineToMeshClient.java"),
                StandardCharsets.UTF_8);
        assertFalse(source.contains("WandClientReceiver.reset()"));
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
