package com.onecuber.mcgltf.wand;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WandInteractionIntegrationTest {
    @Test
    void leftClickAlwaysCancelsBreakingButMutatesOnlyOnServerStart() throws Exception {
        String source = Files.readString(projectRoot().resolve(
                "src/main/java/com/onecuber/mcgltf/wand/WandInteractionHandler.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("event.setCanceled(true)"));
        assertTrue(source.contains("LeftClickBlock.Action.START"));
        assertTrue(source.contains("!event.getLevel().isClientSide()"));
        assertTrue(source.contains("Endpoint.POS1"));
    }

    @Test
    void airClicksProjectTwoBlocksAheadWithoutChangingShiftPriority()
            throws Exception {
        String input = Files.readString(projectRoot().resolve(
                "src/main/java/com/onecuber/mcgltf/client/wand/WandClientInput.java"),
                StandardCharsets.UTF_8);
        String item = Files.readString(projectRoot().resolve(
                "src/main/java/com/onecuber/mcgltf/wand/ExportWandItem.java"),
                StandardCharsets.UTF_8);
        assertTrue(input.contains("new ClearWandSelectionPayload(hand)"));
        assertTrue(input.contains(
                "new SetWandAirEndpointPayload(hand, Endpoint.POS1)"));
        assertTrue(item.contains("Endpoint.POS2"));
        assertTrue(item.contains("WandAirTarget.twoBlocksAhead("));
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
