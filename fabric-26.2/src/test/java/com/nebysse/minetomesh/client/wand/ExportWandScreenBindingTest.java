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
        String source = source();
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
        String source = source();
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
    void screenConsumesGameBindingsAndRoutesInputToFocusedEditor() throws Exception {
        String source = source();
        assertTrue(source.contains("WandKeyboardPolicy.keyPressed(event.key()"));
        assertFalse(source.contains("KeyMapping"));
        assertTrue(source.contains("editor.charTyped(event)"));
        assertTrue(source.contains("editor.keyReleased(event)"));
        assertFalse(source.contains("super.charTyped(event)"));
        assertFalse(source.contains("super.keyReleased(event)"));
    }

    private static String source() throws Exception {
        return Files.readString(moduleRoot().resolve(
                "src/client/java/com/nebysse/minetomesh/client/wand/ExportWandScreen.java"),
                StandardCharsets.UTF_8);
    }

    private static Path moduleRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("fabric-26.2/build.gradle"))) {
                return current.resolve("fabric-26.2");
            }
            if (current.getFileName() != null
                    && current.getFileName().toString().equals("fabric-26.2")
                    && Files.isRegularFile(current.resolve("build.gradle"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new AssertionError("Could not locate the Fabric module");
    }
}
