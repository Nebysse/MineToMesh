package com.onecuber.mcgltf.client.workstation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class WorkstationVisualIntegrationTest {
    private static final Pattern USED_TEXTURE = Pattern.compile(
            "WorkstationTextures\\.(GUI_\\d{3})");
    private static final Pattern ENTRY = Pattern.compile(
            "^  - name: (gui_\\d{3}\\.png)$");
    private static final Pattern HASH = Pattern.compile(
            "^    sha256: ([0-9a-f]{64})$");

    @Test
    void productionResourcesContainEveryApprovedOnePixelSlice() throws Exception {
        Map<String, String> expectedHashes = loadApprovedSliceHashes();
        assertEquals(77, expectedHashes.size(), "approved slice manifest must list 77 slices");
        Path production = projectRoot().resolve(
                "src/main/resources/assets/mcgltf/textures/gui/workstation");
        for (Map.Entry<String, String> entry : expectedHashes.entrySet()) {
            Path installed = production.resolve(entry.getKey());
            assertTrue(Files.isRegularFile(installed),
                    "missing production GUI slice " + entry.getKey());
            assertEquals(entry.getValue(), sha256(installed),
                    "production slice must match approved slices-1x source " + entry.getKey());
        }
    }

    @Test
    void screenUsesTheApprovedSkinInsteadOfVanillaPanels() throws Exception {
        String source = Files.readString(projectRoot().resolve(
                "src/main/java/com/onecuber/mcgltf/client/workstation/ExportWorkstationScreen.java"),
                StandardCharsets.UTF_8);
        Matcher matcher = USED_TEXTURE.matcher(source);
        Set<String> used = new HashSet<>();
        while (matcher.find()) {
            used.add(matcher.group(1));
        }
        assertTrue(used.size() >= 20,
                "screen must wire at least 20 approved visual slices, found " + used.size());
        assertTrue(source.contains("setBordered(false)"),
                "coordinate and name fields must use the custom input skin");
        assertTrue(source.contains("blitNineSlice("),
                "resizable controls must preserve corners with nine-slice rendering");
        assertTrue(source.contains("blitStretchedRegion("),
                "resizable centers and edges must stretch only bounded safe regions");
        assertFalse(source.contains("blitTiledRegion("),
                "decorative center and edge pixels must not repeat across controls");
        assertFalse(source.contains("blitScaled("),
                "approved pixel slices must never be stretched as whole images");
        assertFalse(source.contains("fill(graphics, Layout.HEADER"),
                "plain fallback panels must not replace the approved skin");
    }

    private static Map<String, String> loadApprovedSliceHashes() throws Exception {
        Path manifest = projectRoot().resolve(
                "docs/superpowers/design-assets/minetomesh-0.3.0/workstation-gui-slices-1x-sha256.yml");
        Map<String, String> hashes = new LinkedHashMap<>();
        String current = null;
        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            Matcher entry = ENTRY.matcher(line);
            if (entry.matches()) {
                current = entry.group(1);
                continue;
            }
            Matcher hash = HASH.matcher(line);
            if (hash.matches() && current != null) {
                hashes.put(current, hash.group(1));
                current = null;
            }
        }
        return hashes;
    }

    private static String sha256(Path file) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(file));
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
