package com.nebysse.minetomesh.usd;

import com.nebysse.minetomesh.scene.MaterialKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class UsdaNames {
    private UsdaNames() {
    }

    public static String identifier(String value) {
        Objects.requireNonNull(value, "value");
        String sanitized = value.replaceAll("[^A-Za-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (sanitized.isEmpty()) {
            return "unnamed";
        }
        return Character.isDigit(sanitized.charAt(0)) ? "_" + sanitized : sanitized;
    }

    public static String material(MaterialKey key) {
        Objects.requireNonNull(key, "key");
        String source = key.texture().kind() + "|" + key.texture().sourceId() + "|"
                + key.texture().outputPath() + "|" + key.alphaMode() + "|"
                + key.alphaCutoff().map(String::valueOf).orElse("") + "|"
                + key.doubleSided() + "|" + key.emissive() + "|"
                + key.blendSemantic() + "|" + key.samplerMode();
        String prefix = identifier(key.texture().sourceId());
        if (prefix.length() > 32) {
            prefix = prefix.substring(prefix.length() - 32);
        }
        return "m_" + prefix + "_" + sha256(source).substring(0, 16);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
