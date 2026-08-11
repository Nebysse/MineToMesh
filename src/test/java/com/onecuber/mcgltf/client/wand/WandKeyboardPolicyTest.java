package com.onecuber.mcgltf.client.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class WandKeyboardPolicyTest {
    @Test
    void mapsOnlyEscapeEnterAndTabToScreenActions() {
        assertEquals(WandKeyboardPolicy.Action.CLOSE,
                WandKeyboardPolicy.keyPressed(GLFW.GLFW_KEY_ESCAPE, true));
        assertEquals(WandKeyboardPolicy.Action.COMMIT,
                WandKeyboardPolicy.keyPressed(GLFW.GLFW_KEY_ENTER, true));
        assertEquals(WandKeyboardPolicy.Action.COMMIT,
                WandKeyboardPolicy.keyPressed(GLFW.GLFW_KEY_KP_ENTER, true));
        assertEquals(WandKeyboardPolicy.Action.MOVE_FOCUS,
                WandKeyboardPolicy.keyPressed(GLFW.GLFW_KEY_TAB, false));
    }

    @Test
    void forwardsEditingKeysOnlyWhenAnEditBoxIsFocused() {
        assertEquals(WandKeyboardPolicy.Action.FORWARD_TO_EDITOR,
                WandKeyboardPolicy.keyPressed(GLFW.GLFW_KEY_E, true));
        assertEquals(WandKeyboardPolicy.Action.FORWARD_TO_EDITOR,
                WandKeyboardPolicy.keyPressed(GLFW.GLFW_KEY_BACKSPACE, true));
        assertEquals(WandKeyboardPolicy.Action.CONSUME,
                WandKeyboardPolicy.keyPressed(GLFW.GLFW_KEY_E, false));
        assertEquals(WandKeyboardPolicy.Action.CONSUME,
                WandKeyboardPolicy.keyPressed(GLFW.GLFW_KEY_1, false));
        assertEquals(WandKeyboardPolicy.Action.CONSUME,
                WandKeyboardPolicy.keyPressed(GLFW.GLFW_KEY_W, false));
    }
}
