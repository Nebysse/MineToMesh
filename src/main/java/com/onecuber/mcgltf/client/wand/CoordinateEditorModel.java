package com.onecuber.mcgltf.client.wand;

import java.util.Objects;
import java.util.OptionalInt;

public final class CoordinateEditorModel {
    private String rawText = "";
    private int lastServerValue;
    private boolean editing;
    private boolean invalid;

    public void serverValue(int value) {
        lastServerValue = value;
        if (!editing) {
            rawText = Integer.toString(value);
            invalid = false;
        }
    }

    public EditResult setText(String text) {
        this.rawText = Objects.requireNonNull(text, "text");
        this.invalid = !isParsable(text);
        return new EditResult(invalid);
    }

    public OptionalInt commit() {
        if (invalid) {
            return OptionalInt.empty();
        }
        try {
            int value = Integer.parseInt(rawText.trim());
            lastServerValue = value;
            return OptionalInt.of(value);
        } catch (NumberFormatException exception) {
            invalid = true;
            return OptionalInt.empty();
        }
    }

    public int step(int direction, boolean shift) {
        int delta = shift ? Math.multiplyExact(10, direction) : direction;
        try {
            int current = commit().orElse(lastServerValue);
            int next = Math.addExact(current, delta);
            lastServerValue = next;
            rawText = Integer.toString(next);
            invalid = false;
            return next;
        } catch (ArithmeticException overflow) {
            invalid = true;
            return lastServerValue;
        }
    }

    public void beginEdit() {
        editing = true;
    }

    public void endEdit() {
        editing = false;
        if (!invalid) {
            commit();
        }
    }

    public String rawText() {
        return rawText;
    }

    public boolean isInvalid() {
        return invalid;
    }

    public int lastServerValue() {
        return lastServerValue;
    }

    private static boolean isParsable(String text) {
        if (text.isBlank()) {
            return false;
        }
        try {
            Integer.parseInt(text.trim());
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    public record EditResult(boolean invalid) {
        public boolean isInvalid() {
            return invalid;
        }
    }
}
