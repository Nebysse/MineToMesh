package com.nebysse.minetomesh.client.wand;

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
                "src/main/java/com/nebysse/minetomesh/client/wand/ExportWandScreen.java"),
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
    void lockToggleIsLocalAndIndependentFromWandPayloads() throws Exception {
        String source = Files.readString(projectRoot().resolve(
                "src/main/java/com/nebysse/minetomesh/client/wand/ExportWandScreen.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("LockedSelectionService lockedSelectionService"));
        assertTrue(source.contains("this.lockedSelectionService = Objects.requireNonNull("));
        assertTrue(source.contains("toggleLockedSelection()"));
        assertTrue(source.contains("Component.literal(\"手持预览\")"));
        assertTrue(source.contains("Component.literal(\"锁定选区\")"));
        assertTrue(source.contains("Component.literal(\"导出玩家\")"));
        int method = source.indexOf("private void toggleLockedSelection()");
        int nextMethod = source.indexOf("\n    private ", method + 1);
        String body = source.substring(method, nextMethod);
        assertFalse(body.contains("send("));
        assertFalse(body.contains("Payload"));
    }

    @Test
    void screenConsumesGameBindingsAndRoutesTextDirectlyToFocusedEditor() throws Exception {
        String source = Files.readString(projectRoot().resolve(
                "src/main/java/com/nebysse/minetomesh/client/wand/ExportWandScreen.java"),
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
