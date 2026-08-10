package com.onecuber.mcgltf.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkstationClientReceiverLifecycleTest {
    @Test
    void payloadReceiverCannotBeResetBetweenWorldConnections() {
        assertThrows(NoSuchMethodException.class,
                () -> WorkstationClientReceiver.class.getDeclaredMethod("reset"));
    }

    @Test
    void loggingOutPreservesProcessLifetimePayloadHandlers() throws Exception {
        String source = Files.readString(projectRoot().resolve(
                "src/main/java/com/onecuber/mcgltf/client/McGltfClient.java"),
                StandardCharsets.UTF_8);
        assertFalse(source.contains("WorkstationClientReceiver.reset()"),
                "logging out must clear per-world controller state without erasing "
                        + "the process-lifetime client payload handlers");
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
