package com.onecuber.mcgltf.client.workstation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkstationNineSliceTest {
    @Test
    void everyUsedResizableTextureHasAnExplicitSemanticSafeRegion() throws Exception {
        String source = Files.readString(projectRoot().resolve(
                "src/main/java/com/onecuber/mcgltf/client/workstation/ExportWorkstationScreen.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("NineSliceStyle"),
                "screen must declare explicit nine-slice styles per texture role");
        assertTrue(source.contains("buttonStyle("),
                "button skins need separate safe regions from panel skins");
        assertTrue(source.contains("fieldStyle("),
                "input skins need separate safe regions from panel skins");
        assertTrue(source.contains("panelStyle("),
                "panel skins need explicit panel safe regions");
        assertTrue(source.contains("blitStretchedRegion("),
                "nine-slice edges and centers must stretch only their bounded source regions");
        assertTrue(source.contains("new NineSliceStyle(8)"),
                "resizable skins must target eight physical border pixels");
        assertTrue(source.contains("WorkstationBorderPolicy.logicalBorder("),
                "screen must convert physical border pixels at render time");
        assertTrue(source.contains("Minecraft.getInstance().getWindow().getGuiScale()"),
                "border conversion must use the active Minecraft GUI scale");
        assertFalse(source.contains("new NineSliceStyle(1)"));
        assertFalse(source.contains("new NineSliceStyle(2)"));
        assertFalse(source.contains("new NineSliceStyle(3)"));
        assertFalse(source.contains("new NineSliceStyle(4)"));
        assertTrue(source.contains("blitNineSlice(graphics, skin, fieldStyleFor(index)"),
                "field skins must always flow through the explicit field safe-region helper");
        assertFalse(source.contains("boolean tileEdges"),
                "standard nine-slice regions must not repeat decorative edge pixels");
        assertFalse(source.contains("boolean tileCenter"),
                "standard nine-slice regions must not repeat decorative center pixels");
        assertFalse(source.contains("getWidth(), getHeight(), 4"),
                "button rendering must not use the legacy hard-coded 4-pixel border");
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
