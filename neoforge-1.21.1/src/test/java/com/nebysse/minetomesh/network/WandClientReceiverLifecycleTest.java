package com.nebysse.minetomesh.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WandClientReceiverLifecycleTest {
    @Test
    void receiverCannotBeResetBetweenWorldConnections() {
        assertThrows(NoSuchMethodException.class,
                () -> WandClientReceiver.class.getDeclaredMethod("reset"));
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
