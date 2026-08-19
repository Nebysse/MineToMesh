package com.nebysse.minetomesh.client.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorldProfileKeyTest {
    @Test
    void equivalentSingleplayerPathsAndServerAddressesHashIdentically() {
        assertEquals(
                WorldProfileKey.singleplayer(Path.of("world", ".", "region", "..")),
                WorldProfileKey.singleplayer(Path.of("world")));
        assertEquals(
                WorldProfileKey.multiplayer("Example.COM"),
                WorldProfileKey.multiplayer("example.com:25565"));
        assertTrue(WorldProfileKey.multiplayer("example.com:25565")
                .value().matches("[0-9a-f]{64}"));
    }

    @Test
    void preservesBracketedIpv6AndRejectsInvalidAddresses() {
        assertEquals(
                WorldProfileKey.multiplayer(" [2001:DB8::1] "),
                WorldProfileKey.multiplayer("[2001:db8::1]:25565"));
        assertThrows(IllegalArgumentException.class,
                () -> WorldProfileKey.multiplayer(" "));
        assertThrows(IllegalArgumentException.class,
                () -> WorldProfileKey.multiplayer("example.com:0"));
        assertThrows(IllegalArgumentException.class,
                () -> WorldProfileKey.multiplayer("example.com:65536"));
    }

    @Test
    void profileStringNeverContainsRawContext() {
        WorldProfileKey key = WorldProfileKey.multiplayer("private.example:25570");
        assertFalse(key.toString().contains("private.example"));
        assertEquals(key.value(), key.toString());
    }
}
