package com.onecuber.mcgltf.obj;

import java.util.Objects;

public final class ObjNames {
    private ObjNames() {
    }

    public static String sanitize(String value) {
        Objects.requireNonNull(value, "value");
        String sanitized = value.replaceAll("[^A-Za-z0-9._-]+", "_")
                .replaceAll("^_+|_+$", "");
        return sanitized.isEmpty() ? "unnamed" : sanitized;
    }
}
