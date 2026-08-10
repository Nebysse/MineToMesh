package com.onecuber.mcgltf.client.workstation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkstationActionSkinTest {
    @Test
    void largeActionButtonsKeepTheNativeSixteenPixelHeight() {
        assertEquals(16, ExportWorkstationScreen.Layout.exportButton().height());
        assertEquals(16, ExportWorkstationScreen.Layout.cancelButton().height());
        assertEquals(16, ExportWorkstationScreen.Layout.overlayButton().height());
    }

    @Test
    void overlayUsesOneBaseSkinAndNativeOffOnIndicators() throws Exception {
        String source = Files.readString(projectRoot().resolve(
                "src/main/java/com/onecuber/mcgltf/client/workstation/ExportWorkstationScreen.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("skinnedToggleButton("),
                "overlay must use a dedicated toggle renderer");
        assertTrue(source.contains("WorkstationTextures.GUI_059"),
                "toggle must render the approved OFF indicator at native size");
        assertTrue(source.contains("WorkstationTextures.GUI_060"),
                "toggle must render the approved ON indicator at native size");
        assertTrue(source.contains("blitNatural(graphics, indicator"),
                "toggle indicator must never be nine-sliced or stretched");
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
