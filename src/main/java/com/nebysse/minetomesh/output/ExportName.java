package com.nebysse.minetomesh.output;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class ExportName {
    private static final int MAX_CODE_POINTS = 64;
    private static final Set<String> WINDOWS_DEVICE_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private final String value;

    private ExportName(String value) {
        this.value = value;
    }

    public static ExportName parse(String input) {
        Objects.requireNonNull(input, "input");
        String value = Normalizer.normalize(input, Normalizer.Form.NFC);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Export name must not be empty");
        }
        if (value.equals(".") || value.equals("..")) {
            throw new IllegalArgumentException("Export name must not be a relative path segment");
        }
        if (value.endsWith(".") || value.endsWith(" ")) {
            throw new IllegalArgumentException("Export name must not end with a dot or space");
        }
        if (value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Export name must not contain path separators");
        }
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Export name must not contain control characters");
        }
        if (value.codePointCount(0, value.length()) > MAX_CODE_POINTS) {
            throw new IllegalArgumentException("Export name must contain at most 64 Unicode code points");
        }
        int firstDot = value.indexOf('.');
        String stem = firstDot >= 0 ? value.substring(0, firstDot) : value;
        if (WINDOWS_DEVICE_NAMES.contains(stem.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Export name uses a reserved Windows device name");
        }
        return new ExportName(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ExportName name && value.equals(name.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
