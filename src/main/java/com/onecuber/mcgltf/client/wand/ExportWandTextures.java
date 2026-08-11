package com.onecuber.mcgltf.client.wand;

import com.onecuber.mcgltf.McGltf;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Individual production textures imported verbatim from the approved slices-1x set. */
public final class ExportWandTextures {
    public record Texture(ResourceLocation location, int width, int height) {
        public Texture {
            Objects.requireNonNull(location, "location");
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Texture size must be positive");
            }
        }
    }

    public static final Texture GUI_001 = texture(1, 14, 15);
    public static final Texture GUI_002 = texture(2, 18, 11);
    public static final Texture GUI_003 = texture(3, 15, 15);
    public static final Texture GUI_004 = texture(4, 15, 15);
    public static final Texture GUI_005 = texture(5, 19, 11);
    public static final Texture GUI_006 = texture(6, 15, 15);
    public static final Texture GUI_007 = texture(7, 12, 15);
    public static final Texture GUI_008 = texture(8, 12, 14);
    public static final Texture GUI_009 = texture(9, 81, 12);
    public static final Texture GUI_010 = texture(10, 20, 20);
    public static final Texture GUI_011 = texture(11, 21, 20);
    public static final Texture GUI_012 = texture(12, 11, 19);
    public static final Texture GUI_013 = texture(13, 11, 20);
    public static final Texture GUI_014 = texture(14, 11, 20);
    public static final Texture GUI_015 = texture(15, 11, 20);
    public static final Texture GUI_016 = texture(16, 108, 11);
    public static final Texture GUI_017 = texture(17, 36, 15);
    public static final Texture GUI_018 = texture(18, 35, 15);
    public static final Texture GUI_019 = texture(19, 30, 15);
    public static final Texture GUI_020 = texture(20, 15, 14);
    public static final Texture GUI_021 = texture(21, 15, 14);
    public static final Texture GUI_022 = texture(22, 15, 14);
    public static final Texture GUI_023 = texture(23, 14, 14);
    public static final Texture GUI_024 = texture(24, 18, 12);
    public static final Texture GUI_025 = texture(25, 19, 11);
    public static final Texture GUI_026 = texture(26, 36, 14);
    public static final Texture GUI_027 = texture(27, 35, 14);
    public static final Texture GUI_028 = texture(28, 30, 14);
    public static final Texture GUI_029 = texture(29, 36, 15);
    public static final Texture GUI_030 = texture(30, 35, 15);
    public static final Texture GUI_031 = texture(31, 30, 15);
    public static final Texture GUI_032 = texture(32, 36, 16);
    public static final Texture GUI_033 = texture(33, 35, 16);
    public static final Texture GUI_034 = texture(34, 30, 16);
    public static final Texture GUI_035 = texture(35, 33, 16);
    public static final Texture GUI_036 = texture(36, 32, 16);
    public static final Texture GUI_037 = texture(37, 30, 16);
    public static final Texture GUI_038 = texture(38, 31, 16);
    public static final Texture GUI_039 = texture(39, 18, 16);
    public static final Texture GUI_040 = texture(40, 18, 16);
    public static final Texture GUI_041 = texture(41, 17, 16);
    public static final Texture GUI_042 = texture(42, 17, 16);
    public static final Texture GUI_043 = texture(43, 33, 16);
    public static final Texture GUI_044 = texture(44, 31, 16);
    public static final Texture GUI_045 = texture(45, 30, 16);
    public static final Texture GUI_046 = texture(46, 31, 16);
    public static final Texture GUI_047 = texture(47, 17, 16);
    public static final Texture GUI_048 = texture(48, 17, 16);
    public static final Texture GUI_049 = texture(49, 18, 16);
    public static final Texture GUI_050 = texture(50, 18, 16);
    public static final Texture GUI_051 = texture(51, 12, 13);
    public static final Texture GUI_052 = texture(52, 28, 13);
    public static final Texture GUI_053 = texture(53, 28, 13);
    public static final Texture GUI_054 = texture(54, 12, 13);
    public static final Texture GUI_055 = texture(55, 111, 12);
    public static final Texture GUI_056 = texture(56, 12, 13);
    public static final Texture GUI_057 = texture(57, 12, 13);
    public static final Texture GUI_058 = texture(58, 111, 13);
    public static final Texture GUI_059 = texture(59, 28, 13);
    public static final Texture GUI_060 = texture(60, 28, 13);
    public static final Texture GUI_061 = texture(61, 9, 10);
    public static final Texture GUI_062 = texture(62, 9, 10);
    public static final Texture GUI_063 = texture(63, 26, 9);
    public static final Texture GUI_064 = texture(64, 28, 9);
    public static final Texture GUI_065 = texture(65, 17, 9);
    public static final Texture GUI_066 = texture(66, 67, 9);
    public static final Texture GUI_067 = texture(67, 17, 9);
    public static final Texture GUI_068 = texture(68, 11, 12);
    public static final Texture GUI_069 = texture(69, 11, 12);
    public static final Texture GUI_070 = texture(70, 144, 3);
    public static final Texture GUI_071 = texture(71, 23, 24);
    public static final Texture GUI_072 = texture(72, 21, 22);
    public static final Texture GUI_073 = texture(73, 23, 21);
    public static final Texture GUI_074 = texture(74, 22, 21);
    public static final Texture GUI_075 = texture(75, 18, 20);
    public static final Texture GUI_076 = texture(76, 24, 18);
    public static final Texture GUI_077 = texture(77, 19, 19);

    private static final Map<String, Texture> BY_NAME = new LinkedHashMap<>();

    static {
        for (int index = 1; index <= 77; index++) {
            String name = "gui_%03d".formatted(index);
            try {
                BY_NAME.put(name, (Texture) ExportWandTextures.class
                        .getField(name.toUpperCase()).get(null));
            } catch (ReflectiveOperationException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }
    }

    public static Texture texture(String name) {
        Texture texture = BY_NAME.get(Objects.requireNonNull(name, "name"));
        if (texture == null) {
            throw new IllegalArgumentException("Unknown GUI texture: " + name);
        }
        return texture;
    }

    private static Texture texture(int index, int width, int height) {
        String path = "textures/gui/export_wand/gui_%03d.png".formatted(index);
        return new Texture(ResourceLocation.fromNamespaceAndPath(McGltf.MOD_ID, path), width, height);
    }

    private ExportWandTextures() {
    }
}
