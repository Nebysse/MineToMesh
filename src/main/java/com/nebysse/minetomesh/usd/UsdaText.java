package com.nebysse.minetomesh.usd;

import java.util.Locale;
import java.util.Objects;

public final class UsdaText {
    private UsdaText() {
    }

    public static String quoted(String value) {
        Objects.requireNonNull(value, "value");
        return "\"" + value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n") + "\"";
    }

    public static String asset(String value) {
        Objects.requireNonNull(value, "value");
        return "@" + value.replace("\\", "/").replace("@", "\\@").replace("\n", "") + "@";
    }

    public static String number(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("USDA number must be finite");
        }
        if (value == 0.0F) {
            return "0";
        }
        return String.format(Locale.ROOT, "%.9g", value);
    }

    public static String number(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("USDA number must be finite");
        }
        if (value == 0.0D) {
            return "0";
        }
        return String.format(Locale.ROOT, "%.12g", value);
    }
}
