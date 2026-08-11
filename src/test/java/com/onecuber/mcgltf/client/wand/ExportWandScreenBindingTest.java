package com.onecuber.mcgltf.client.wand;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExportWandScreenBindingTest {
    @Test
    void screenBindsOnceToMenuWandIdentityBeforeCreatingWidgets() throws Exception {
        String source = Files.readString(projectRoot().resolve(
                "src/main/java/com/onecuber/mcgltf/client/wand/ExportWandScreen.java"),
                StandardCharsets.UTF_8);
        int init = source.indexOf("protected void init()");
        int bind = source.indexOf(
                "controller.bind(menu.binding().wandId(), currentDimension())", init);
        int widgets = source.indexOf("createCoordinateWidgets()", init);
        assertTrue(bind > init);
        assertTrue(widgets > bind);
        assertTrue(source.contains("if (!controllerBound)"));
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
