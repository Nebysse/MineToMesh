package com.onecuber.mcgltf.client.wand;

import org.lwjgl.glfw.GLFW;

public final class WandKeyboardPolicy {
    private WandKeyboardPolicy() {
    }

    public static Action keyPressed(int keyCode, boolean editorFocused) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> Action.CLOSE;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> Action.COMMIT;
            case GLFW.GLFW_KEY_TAB -> Action.MOVE_FOCUS;
            default -> editorFocused
                    ? Action.FORWARD_TO_EDITOR
                    : Action.CONSUME;
        };
    }

    public enum Action {
        CLOSE,
        COMMIT,
        MOVE_FOCUS,
        FORWARD_TO_EDITOR,
        CONSUME
    }
}
