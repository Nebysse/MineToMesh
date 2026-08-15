package com.nebysse.minetomesh.client.selection;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

public record WorldProfileKey(String value) {
    private static final int DEFAULT_PORT = 25565;

    public WorldProfileKey {
        Objects.requireNonNull(value, "value");
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Profile key must be a lowercase SHA-256 hash");
        }
    }

    public static WorldProfileKey singleplayer(Path worldRoot) {
        Objects.requireNonNull(worldRoot, "worldRoot");
        String normalized = worldRoot.toAbsolutePath().normalize().toString();
        return hash("singleplayer\0" + normalized);
    }

    public static WorldProfileKey multiplayer(String address) {
        return hash("multiplayer\0" + normalizeAddress(address));
    }

    private static String normalizeAddress(String address) {
        String text = Objects.requireNonNull(address, "address").trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Server address must not be blank");
        }
        String host;
        String portText = null;
        if (text.startsWith("[")) {
            int closing = text.indexOf(']');
            if (closing <= 1) {
                throw new IllegalArgumentException("Invalid bracketed server address");
            }
            host = text.substring(0, closing + 1).toLowerCase(Locale.ROOT);
            String remainder = text.substring(closing + 1).trim();
            if (!remainder.isEmpty()) {
                if (!remainder.startsWith(":") || remainder.length() == 1) {
                    throw new IllegalArgumentException("Invalid server port");
                }
                portText = remainder.substring(1).trim();
            }
        } else {
            int firstColon = text.indexOf(':');
            int lastColon = text.lastIndexOf(':');
            if (firstColon != lastColon) {
                throw new IllegalArgumentException("IPv6 server hosts must be bracketed");
            }
            if (lastColon >= 0) {
                host = text.substring(0, lastColon).trim().toLowerCase(Locale.ROOT);
                portText = text.substring(lastColon + 1).trim();
            } else {
                host = text.toLowerCase(Locale.ROOT);
            }
        }
        if (host.isBlank()) {
            throw new IllegalArgumentException("Server host must not be blank");
        }
        int port = DEFAULT_PORT;
        if (portText != null) {
            if (portText.isEmpty() || !portText.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("Invalid server port");
            }
            try {
                port = Integer.parseInt(portText);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid server port", exception);
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Server port must be in range 1..65535");
            }
        }
        return host + ":" + port;
    }

    private static WorldProfileKey hash(String source) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return new WorldProfileKey(HexFormat.of().formatHex(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
