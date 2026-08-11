package com.onecuber.mcgltf.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ExportWandResourceTest {
    @Test
    void recipeUsesApprovedSurveyWandPatternAndIngredients() throws Exception {
        JsonObject recipe = json("src/main/resources/data/mcgltf/recipe/export_wand.json");
        JsonArray pattern = recipe.getAsJsonArray("pattern");
        assertEquals(List.of("  A", " RC", "S  "), List.of(
                pattern.get(0).getAsString(), pattern.get(1).getAsString(),
                pattern.get(2).getAsString()));
        JsonObject key = recipe.getAsJsonObject("key");
        assertEquals("minecraft:amethyst_shard", item(key, "A"));
        assertEquals("minecraft:redstone", item(key, "R"));
        assertEquals("minecraft:copper_ingot", item(key, "C"));
        assertEquals("minecraft:stick", item(key, "S"));
        assertEquals("mcgltf:export_wand",
                recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
    }

    @Test
    void itemModelReferencesDedicatedTexture() throws Exception {
        JsonObject model = json(
                "src/main/resources/assets/mcgltf/models/item/export_wand.json");
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals("mcgltf:item/export_wand",
                model.getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void itemTextureIsCrispSixteenPixelRgbaArtwork() throws Exception {
        Path texture = projectRoot().resolve(
                "src/main/resources/assets/mcgltf/textures/item/export_wand.png");
        BufferedImage image = ImageIO.read(texture.toFile());
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha());
        int visible = 0;
        boolean hasOrange = false;
        boolean hasBlue = false;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) != 0) {
                    visible++;
                }
                int rgb = argb & 0xFFFFFF;
                hasOrange |= rgb == 0xED741C || rgb == 0xF08A33;
                hasBlue |= rgb == 0x3488D8 || rgb == 0x3C9BEC;
            }
        }
        assertTrue(visible >= 35 && visible <= 90,
                "wand silhouette must be legible without filling the icon");
        assertTrue(hasOrange, "texture must carry the POS1 orange accent");
        assertTrue(hasBlue, "texture must carry the POS2 blue accent");
    }

    @Test
    void languagesNameTheExportWand() throws Exception {
        JsonObject zh = json("src/main/resources/assets/mcgltf/lang/zh_cn.json");
        JsonObject en = json("src/main/resources/assets/mcgltf/lang/en_us.json");
        assertEquals("导出魔杖", zh.get("item.mcgltf.export_wand").getAsString());
        assertEquals("Export Wand", en.get("item.mcgltf.export_wand").getAsString());
    }

    private static String item(JsonObject key, String symbol) {
        return key.getAsJsonObject(symbol).get("item").getAsString();
    }

    private static JsonObject json(String relativePath) throws Exception {
        return JsonParser.parseString(Files.readString(
                projectRoot().resolve(relativePath), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
