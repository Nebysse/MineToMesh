package com.onecuber.mcgltf.client.wand;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void screenConsumesGameBindingsAndRoutesTextDirectlyToFocusedEditor() throws Exception {
        String source = Files.readString(projectRoot().resolve(
                "src/main/java/com/onecuber/mcgltf/client/wand/ExportWandScreen.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("WandKeyboardPolicy.keyPressed"));
        assertFalse(source.contains("KeyMapping"));
        assertTrue(source.contains("editor.charTyped(character, modifiers)"));
        assertTrue(source.contains("editor.keyReleased(keyCode, scanCode, modifiers)"));
        assertFalse(source.contains(
                "super.keyPressed(keyCode, scanCode, modifiers);\n        return true;"));
        assertFalse(source.contains("super.charTyped(character, modifiers)"));
        assertFalse(source.contains("super.keyReleased(keyCode, scanCode, modifiers)"));
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
