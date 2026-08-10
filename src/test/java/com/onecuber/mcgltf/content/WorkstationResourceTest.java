package com.onecuber.mcgltf.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class WorkstationResourceTest {
    private static final String[] FACES = {"front", "side", "back", "top", "bottom"};

    @Test
    void blockTexturesAreExactlySixteenBySixteen() throws Exception {
        for (String face : FACES) {
            try (InputStream input = resource(
                    "/assets/mcgltf/textures/block/export_workstation_" + face + ".png")) {
                BufferedImage image = ImageIO.read(input);
                assertNotNull(image, "missing texture for " + face);
                assertEquals(16, image.getWidth(), "width for " + face);
                assertEquals(16, image.getHeight(), "height for " + face);
            }
        }
    }

    @Test
    void guiAtlasHasAlpha() throws Exception {
        try (InputStream input = resource(
                "/assets/mcgltf/textures/gui/export_workstation.png")) {
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image, "missing GUI atlas");
            assertTrue(image.getColorModel().hasAlpha(), "GUI atlas must carry alpha");
            boolean transparent = false;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) < 255) {
                        transparent = true;
                        break;
                    }
                }
                if (transparent) {
                    break;
                }
            }
            assertTrue(transparent, "GUI atlas must contain transparent pixels");
        }
    }

    @Test
    void productionPngsContainNoExactGreenKeyPixels() throws Exception {
        String[] paths = {
                "/assets/mcgltf/textures/gui/export_workstation.png",
                "/assets/mcgltf/textures/block/export_workstation_front.png",
                "/assets/mcgltf/textures/block/export_workstation_side.png",
                "/assets/mcgltf/textures/block/export_workstation_back.png",
                "/assets/mcgltf/textures/block/export_workstation_top.png",
                "/assets/mcgltf/textures/block/export_workstation_bottom.png"
        };
        for (String path : paths) {
            try (InputStream input = resource(path)) {
                BufferedImage image = ImageIO.read(input);
                assertNotNull(image, "missing " + path);
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int rgb = image.getRGB(x, y) & 0xFFFFFF;
                        assertFalse(rgb == 0x00FF00, "green-key pixel at " + path + ":" + x + "," + y);
                    }
                }
            }
        }
    }

    @Test
    void blockstateExposesFourHorizontalFacings() throws Exception {
        JsonObject blockstate = json("/assets/mcgltf/blockstates/export_workstation.json");
        JsonObject variants = blockstate.getAsJsonObject("variants");
        assertNotNull(variants);
        for (String facing : new String[]{"north", "east", "south", "west"}) {
            assertTrue(variants.has("facing=" + facing),
                    "missing variant facing=" + facing);
        }
    }

    @Test
    void recipeMatchesApprovedIngredientPattern() throws Exception {
        JsonObject recipe = json("/data/mcgltf/recipe/export_workstation.json");
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        JsonArray pattern = recipe.getAsJsonArray("pattern");
        assertEquals("IGI", pattern.get(0).getAsString());
        assertEquals("RCR", pattern.get(1).getAsString());
        assertEquals("III", pattern.get(2).getAsString());
        JsonObject keys = recipe.getAsJsonObject("key");
        assertEquals("minecraft:iron_ingot",
                keys.getAsJsonObject("I").get("item").getAsString());
        assertEquals("minecraft:glass_pane",
                keys.getAsJsonObject("G").get("item").getAsString());
        assertEquals("minecraft:redstone",
                keys.getAsJsonObject("R").get("item").getAsString());
        assertEquals("minecraft:cartography_table",
                keys.getAsJsonObject("C").get("item").getAsString());
        assertEquals("mcgltf:export_workstation",
                recipe.getAsJsonObject("result").get("id").getAsString());
    }

    @Test
    void lootTableDropsTheWorkstation() throws Exception {
        JsonObject loot = json("/data/mcgltf/loot_table/blocks/export_workstation.json");
        JsonArray pools = loot.getAsJsonArray("pools");
        boolean found = false;
        for (var pool : pools) {
            JsonArray entries = pool.getAsJsonObject().getAsJsonArray("entries");
            for (var entry : entries) {
                if ("mcgltf:export_workstation"
                        .equals(entry.getAsJsonObject().get("name").getAsString())) {
                    found = true;
                }
            }
        }
        assertTrue(found, "loot table must drop the workstation");
    }

    @Test
    void mineableTagsIncludeTheWorkstation() throws Exception {
        JsonObject tags = json("/data/minecraft/tags/block/mineable/pickaxe.json");
        JsonArray values = tags.getAsJsonArray("values");
        assertTrue(values.contains(JsonParser.parseString("\"mcgltf:export_workstation\"")),
                "pickaxe tag must include the workstation");
    }

    private static InputStream resource(String path) {
        return WorkstationResourceTest.class.getResourceAsStream(path);
    }

    private static JsonObject json(String path) throws Exception {
        try (InputStream input = resource(path)) {
            assertNotNull(input, "missing resource " + path);
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return JsonParser.parseString(text).getAsJsonObject();
        }
    }
}
