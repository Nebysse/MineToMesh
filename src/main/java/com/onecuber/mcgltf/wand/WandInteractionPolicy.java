package com.onecuber.mcgltf.wand;

import java.util.Objects;

public final class WandInteractionPolicy {
    public enum Button {
        LEFT,
        RIGHT
    }

    public enum Target {
        AIR,
        BLOCK
    }

    public enum Action {
        PASS,
        SET_POS1,
        SET_POS2,
        CLEAR,
        OPEN_GUI
    }

    private WandInteractionPolicy() {
    }

    public static Action decide(boolean shift, Target target, Button button) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(button, "button");
        if (button == Button.RIGHT && shift) {
            return Action.OPEN_GUI;
        }
        if (button == Button.LEFT && shift && target == Target.AIR) {
            return Action.CLEAR;
        }
        if (button == Button.LEFT) {
            return Action.SET_POS1;
        }
        if (button == Button.RIGHT) {
            return Action.SET_POS2;
        }
        return Action.PASS;
    }
}
