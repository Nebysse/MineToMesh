package com.nebysse.minetomesh.client.wand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ExportWandVisualIntegrationTest {
    private static final Pattern ENTRY = Pattern.compile(
            "^  - name: (gui_\\d{3}\\.png)$");
    private static final Pattern HASH = Pattern.compile(
            "^    sha256: ([0-9a-f]{64})$");

    @Test
    void allApprovedSlicesMovedByteForByteToWandNamespace() throws Exception {
        Map<String, String> hashes = approvedHashes();
        assertEquals(77, hashes.size());
        Path folder = projectRoot().resolve(
                "src/main/resources/assets/minetomesh/textures/gui/export_wand");
        for (Map.Entry<String, String> entry : hashes.entrySet()) {
            Path file = folder.resolve(entry.getKey());
            assertTrue(Files.isRegularFile(file), "missing " + entry.getKey());
            assertEquals(entry.getValue(), sha256(file), entry.getKey());
        }
        String textures = source("client/wand/ExportWandTextures.java");
        assertTrue(textures.contains(
                "textures/gui/export_wand/gui_%03d.png"));
    }

    @Test
    void forcefieldShellUsesVanillaWorldBorderTexturePipelineAndWorldSpaceUvs() throws Exception {
        String renderer = source("client/wand/SelectionOverlayRenderer.java");
        assertTrue(renderer.contains("DefaultVertexFormat.POSITION_TEX"));
        assertTrue(renderer.contains("GameRenderer::getPositionTexShader"));
        assertTrue(renderer.contains("RenderSystem.setShaderColor("));
        assertTrue(renderer.contains("worldUv("));
        assertFalse(renderer.contains("RenderType.entityTranslucent(FORCEFIELD)"));
    }

    @Test
    void activeScreenKeepsLocalDraftsAndShellUsesTextureAlphaUnmodified() throws Exception {
        String screen = source("client/wand/ExportWandScreen.java");
        int tick = screen.indexOf("public void containerTick()");
        int end = screen.indexOf("private boolean commitFocusLosses()", tick);
        String tickBody = screen.substring(tick, end);
        assertFalse(tickBody.contains("refreshFromMenu()"));
        assertFalse(tickBody.contains("syncOverlayFromMenu()"));
        String renderer = source("client/wand/SelectionOverlayRenderer.java");
        assertTrue(renderer.contains("FACE_BLUE / 255.0F, 1.0F)"));
    }

    @Test
    void screenUsesWandPayloadsWithoutFeetControls() throws Exception {
        String screen = source("client/wand/ExportWandScreen.java");
        assertFalse(screen.contains("feetButton"));
        assertFalse(screen.contains("CaptureFeetPayload"));
        assertTrue(screen.contains("UpdateWandEndpointPayload"));
        assertTrue(screen.contains("UpdateWandExportNamePayload"));
        assertTrue(screen.contains("ToggleWandOverlayPayload"));
        assertTrue(screen.contains("MineToMesh.DISPLAY_NAME + \" \" + MineToMesh.VERSION"));
        assertTrue(screen.contains("new NineSliceStyle(8)"));
        assertTrue(screen.contains("blitStretchedRegion("));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(projectRoot().resolve(
                "src/main/java/com/nebysse/minetomesh/" + relative),
                StandardCharsets.UTF_8);
    }

    private static Map<String, String> approvedHashes() throws Exception {
        Path manifest = projectRoot().getParent().resolve(
                "docs/superpowers/design-assets/minetomesh-0.3.0/"
                        + "workstation-gui-slices-1x-sha256.yml");
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
        StringBuilder builder = new StringBuilder();
        for (byte value : digest) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
