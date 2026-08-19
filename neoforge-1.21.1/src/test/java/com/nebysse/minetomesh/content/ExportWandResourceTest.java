package com.nebysse.minetomesh.content;

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
        JsonObject recipe = json("src/main/resources/data/minetomesh/recipe/export_wand.json");
        JsonArray pattern = recipe.getAsJsonArray("pattern");
        assertEquals(List.of("  A", " RC", "S  "), List.of(
                pattern.get(0).getAsString(), pattern.get(1).getAsString(),
                pattern.get(2).getAsString()));
        JsonObject key = recipe.getAsJsonObject("key");
        assertEquals("minecraft:amethyst_shard", item(key, "A"));
        assertEquals("minecraft:redstone", item(key, "R"));
        assertEquals("minecraft:copper_ingot", item(key, "C"));
        assertEquals("minecraft:stick", item(key, "S"));
        assertEquals("minetomesh:export_wand",
                recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
    }

    @Test
    void itemModelReferencesDedicatedTexture() throws Exception {
        JsonObject model = json(
                "src/main/resources/assets/minetomesh/models/item/export_wand.json");
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        assertEquals("minetomesh:item/export_wand",
                model.getAsJsonObject("textures").get("layer0").getAsString());
    }

    @Test
    void itemTextureIsCrispThirtyTwoPixelRgbaArtworkWithoutGreenScreenResidue() throws Exception {
        Path texture = projectRoot().resolve(
                "src/main/resources/assets/minetomesh/textures/item/export_wand.png");
        BufferedImage image = ImageIO.read(texture.toFile());
        assertEquals(32, image.getWidth());
        assertEquals(32, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha());
        int transparent = 0;
        int opaque = 0;
        int semiTransparent = 0;
        int green = 0;
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int argb = image.getRGB(x, y);
                int alpha = argb >>> 24;
                if (alpha == 0) {
                    transparent++;
                } else if (alpha == 255) {
                    opaque++;
                } else {
                    semiTransparent++;
                }
                int red = (argb >>> 16) & 0xFF;
                int greenChannel = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                if (alpha > 0 && greenChannel > 200 && red < 30 && blue < 30) {
                    green++;
                }
            }
        }
        assertEquals(760, transparent);
        assertEquals(264, opaque);
        assertEquals(0, semiTransparent);
        assertEquals(0, green);
    }

    @Test
    void languagesNameTheExportWand() throws Exception {
        JsonObject zh = json("src/main/resources/assets/minetomesh/lang/zh_cn.json");
        JsonObject en = json("src/main/resources/assets/minetomesh/lang/en_us.json");
        assertEquals("导出魔杖", zh.get("item.minetomesh.export_wand").getAsString());
        assertEquals("Export Wand", en.get("item.minetomesh.export_wand").getAsString());
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
