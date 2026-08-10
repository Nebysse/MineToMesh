package com.onecuber.mcgltf.client.workstation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkstationScreenBindingTest {
    @Test
    void screenBindsControllerOnceBeforeSendingExportRequests() throws Exception {
        String source = Files.readString(projectRoot().resolve(
                "src/main/java/com/onecuber/mcgltf/client/workstation/ExportWorkstationScreen.java"),
                StandardCharsets.UTF_8);
        int init = source.indexOf("protected void init()");
        int bind = source.indexOf("controller.bind(menu.stationPos(), currentDimension())", init);
        int widgets = source.indexOf("createCoordinateWidgets()", init);
        assertTrue(bind > init, "screen must bind the shared export controller");
        assertTrue(widgets > bind, "controller binding must precede interactive widget creation");
        assertTrue(source.contains("if (!controllerBound)"),
                "screen resize must not reset an active export by binding twice");
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
