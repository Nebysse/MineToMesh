package com.onecuber.mcgltf.client.workstation;

import com.onecuber.mcgltf.McGltf;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public final class WorkstationTextures {
    public static final ResourceLocation GUI_ATLAS = ResourceLocation.fromNamespaceAndPath(
            McGltf.MOD_ID, "textures/gui/export_workstation.png");

    public static final int ATLAS_WIDTH = 384;
    public static final int ATLAS_HEIGHT = 120;

    public record Sprite(int x, int y, int width, int height) {
        public Sprite {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("Sprite size must not be negative");
            }
        }
    }

    public static final Sprite GUI_001 = new Sprite(0, 0, 14, 15);
    public static final Sprite GUI_002 = new Sprite(24, 0, 18, 11);
    public static final Sprite GUI_003 = new Sprite(48, 0, 15, 15);
    public static final Sprite GUI_004 = new Sprite(72, 0, 15, 15);
    public static final Sprite GUI_005 = new Sprite(96, 0, 19, 11);
    public static final Sprite GUI_006 = new Sprite(120, 0, 15, 15);
    public static final Sprite GUI_007 = new Sprite(144, 0, 12, 15);
    public static final Sprite GUI_008 = new Sprite(168, 0, 12, 14);
    public static final Sprite GUI_009 = new Sprite(192, 0, 81, 12);
    public static final Sprite GUI_010 = new Sprite(216, 0, 20, 20);
    public static final Sprite GUI_011 = new Sprite(240, 0, 21, 20);
    public static final Sprite GUI_012 = new Sprite(264, 0, 11, 19);
    public static final Sprite GUI_013 = new Sprite(288, 0, 11, 20);
    public static final Sprite GUI_014 = new Sprite(312, 0, 11, 20);
    public static final Sprite GUI_015 = new Sprite(336, 0, 11, 20);
    public static final Sprite GUI_016 = new Sprite(360, 0, 108, 11);
    public static final Sprite GUI_017 = new Sprite(0, 24, 36, 15);
    public static final Sprite GUI_018 = new Sprite(24, 24, 35, 15);
    public static final Sprite GUI_019 = new Sprite(48, 24, 30, 15);
    public static final Sprite GUI_020 = new Sprite(72, 24, 15, 14);
    public static final Sprite GUI_021 = new Sprite(96, 24, 15, 14);
    public static final Sprite GUI_022 = new Sprite(120, 24, 15, 14);
    public static final Sprite GUI_023 = new Sprite(144, 24, 14, 14);
    public static final Sprite GUI_024 = new Sprite(168, 24, 18, 12);
    public static final Sprite GUI_025 = new Sprite(192, 24, 19, 11);
    public static final Sprite GUI_026 = new Sprite(216, 24, 36, 14);
    public static final Sprite GUI_027 = new Sprite(240, 24, 35, 14);
    public static final Sprite GUI_028 = new Sprite(264, 24, 30, 14);
    public static final Sprite GUI_029 = new Sprite(288, 24, 36, 15);
    public static final Sprite GUI_030 = new Sprite(312, 24, 35, 15);
    public static final Sprite GUI_031 = new Sprite(336, 24, 30, 15);
    public static final Sprite GUI_032 = new Sprite(360, 24, 36, 16);
    public static final Sprite GUI_033 = new Sprite(0, 48, 35, 16);
    public static final Sprite GUI_034 = new Sprite(24, 48, 30, 16);
    public static final Sprite GUI_035 = new Sprite(48, 48, 33, 16);
    public static final Sprite GUI_036 = new Sprite(72, 48, 32, 16);
    public static final Sprite GUI_037 = new Sprite(96, 48, 30, 16);
    public static final Sprite GUI_038 = new Sprite(120, 48, 31, 16);
    public static final Sprite GUI_039 = new Sprite(144, 48, 18, 16);
    public static final Sprite GUI_040 = new Sprite(168, 48, 18, 16);
    public static final Sprite GUI_041 = new Sprite(192, 48, 17, 16);
    public static final Sprite GUI_042 = new Sprite(216, 48, 17, 16);
    public static final Sprite GUI_043 = new Sprite(240, 48, 33, 16);
    public static final Sprite GUI_044 = new Sprite(264, 48, 31, 16);
    public static final Sprite GUI_045 = new Sprite(288, 48, 30, 16);
    public static final Sprite GUI_046 = new Sprite(312, 48, 31, 16);
    public static final Sprite GUI_047 = new Sprite(336, 48, 17, 16);
    public static final Sprite GUI_048 = new Sprite(360, 48, 17, 16);
    public static final Sprite GUI_049 = new Sprite(0, 72, 18, 16);
    public static final Sprite GUI_050 = new Sprite(24, 72, 18, 16);
    public static final Sprite GUI_051 = new Sprite(48, 72, 12, 13);
    public static final Sprite GUI_052 = new Sprite(72, 72, 28, 13);
    public static final Sprite GUI_053 = new Sprite(96, 72, 28, 13);
    public static final Sprite GUI_054 = new Sprite(120, 72, 12, 13);
    public static final Sprite GUI_055 = new Sprite(144, 72, 111, 12);
    public static final Sprite GUI_056 = new Sprite(168, 72, 12, 13);
    public static final Sprite GUI_057 = new Sprite(192, 72, 12, 13);
    public static final Sprite GUI_058 = new Sprite(216, 72, 111, 13);
    public static final Sprite GUI_059 = new Sprite(240, 72, 28, 13);
    public static final Sprite GUI_060 = new Sprite(264, 72, 28, 13);
    public static final Sprite GUI_061 = new Sprite(288, 72, 9, 10);
    public static final Sprite GUI_062 = new Sprite(312, 72, 9, 10);
    public static final Sprite GUI_063 = new Sprite(336, 72, 26, 9);
    public static final Sprite GUI_064 = new Sprite(360, 72, 28, 9);
    public static final Sprite GUI_065 = new Sprite(0, 96, 17, 9);
    public static final Sprite GUI_066 = new Sprite(24, 96, 67, 9);
    public static final Sprite GUI_067 = new Sprite(48, 96, 17, 9);
    public static final Sprite GUI_068 = new Sprite(72, 96, 11, 12);
    public static final Sprite GUI_069 = new Sprite(96, 96, 11, 12);
    public static final Sprite GUI_070 = new Sprite(120, 96, 144, 3);
    public static final Sprite GUI_071 = new Sprite(144, 96, 23, 24);
    public static final Sprite GUI_072 = new Sprite(168, 96, 21, 22);
    public static final Sprite GUI_073 = new Sprite(192, 96, 23, 21);
    public static final Sprite GUI_074 = new Sprite(216, 96, 22, 21);
    public static final Sprite GUI_075 = new Sprite(240, 96, 18, 20);
    public static final Sprite GUI_076 = new Sprite(264, 96, 24, 18);
    public static final Sprite GUI_077 = new Sprite(288, 96, 19, 19);

    private static final Map<String, Sprite> BY_NAME = new LinkedHashMap<>();

    static {
        BY_NAME.put("gui_001", GUI_001);
        BY_NAME.put("gui_002", GUI_002);
        BY_NAME.put("gui_003", GUI_003);
        BY_NAME.put("gui_004", GUI_004);
        BY_NAME.put("gui_005", GUI_005);
        BY_NAME.put("gui_006", GUI_006);
        BY_NAME.put("gui_007", GUI_007);
        BY_NAME.put("gui_008", GUI_008);
        BY_NAME.put("gui_009", GUI_009);
        BY_NAME.put("gui_010", GUI_010);
        BY_NAME.put("gui_011", GUI_011);
        BY_NAME.put("gui_012", GUI_012);
        BY_NAME.put("gui_013", GUI_013);
        BY_NAME.put("gui_014", GUI_014);
        BY_NAME.put("gui_015", GUI_015);
        BY_NAME.put("gui_016", GUI_016);
        BY_NAME.put("gui_017", GUI_017);
        BY_NAME.put("gui_018", GUI_018);
        BY_NAME.put("gui_019", GUI_019);
        BY_NAME.put("gui_020", GUI_020);
        BY_NAME.put("gui_021", GUI_021);
        BY_NAME.put("gui_022", GUI_022);
        BY_NAME.put("gui_023", GUI_023);
        BY_NAME.put("gui_024", GUI_024);
        BY_NAME.put("gui_025", GUI_025);
        BY_NAME.put("gui_026", GUI_026);
        BY_NAME.put("gui_027", GUI_027);
        BY_NAME.put("gui_028", GUI_028);
        BY_NAME.put("gui_029", GUI_029);
        BY_NAME.put("gui_030", GUI_030);
        BY_NAME.put("gui_031", GUI_031);
        BY_NAME.put("gui_032", GUI_032);
        BY_NAME.put("gui_033", GUI_033);
        BY_NAME.put("gui_034", GUI_034);
        BY_NAME.put("gui_035", GUI_035);
        BY_NAME.put("gui_036", GUI_036);
        BY_NAME.put("gui_037", GUI_037);
        BY_NAME.put("gui_038", GUI_038);
        BY_NAME.put("gui_039", GUI_039);
        BY_NAME.put("gui_040", GUI_040);
        BY_NAME.put("gui_041", GUI_041);
        BY_NAME.put("gui_042", GUI_042);
        BY_NAME.put("gui_043", GUI_043);
        BY_NAME.put("gui_044", GUI_044);
        BY_NAME.put("gui_045", GUI_045);
        BY_NAME.put("gui_046", GUI_046);
        BY_NAME.put("gui_047", GUI_047);
        BY_NAME.put("gui_048", GUI_048);
        BY_NAME.put("gui_049", GUI_049);
        BY_NAME.put("gui_050", GUI_050);
        BY_NAME.put("gui_051", GUI_051);
        BY_NAME.put("gui_052", GUI_052);
        BY_NAME.put("gui_053", GUI_053);
        BY_NAME.put("gui_054", GUI_054);
        BY_NAME.put("gui_055", GUI_055);
        BY_NAME.put("gui_056", GUI_056);
        BY_NAME.put("gui_057", GUI_057);
        BY_NAME.put("gui_058", GUI_058);
        BY_NAME.put("gui_059", GUI_059);
        BY_NAME.put("gui_060", GUI_060);
        BY_NAME.put("gui_061", GUI_061);
        BY_NAME.put("gui_062", GUI_062);
        BY_NAME.put("gui_063", GUI_063);
        BY_NAME.put("gui_064", GUI_064);
        BY_NAME.put("gui_065", GUI_065);
        BY_NAME.put("gui_066", GUI_066);
        BY_NAME.put("gui_067", GUI_067);
        BY_NAME.put("gui_068", GUI_068);
        BY_NAME.put("gui_069", GUI_069);
        BY_NAME.put("gui_070", GUI_070);
        BY_NAME.put("gui_071", GUI_071);
        BY_NAME.put("gui_072", GUI_072);
        BY_NAME.put("gui_073", GUI_073);
        BY_NAME.put("gui_074", GUI_074);
        BY_NAME.put("gui_075", GUI_075);
        BY_NAME.put("gui_076", GUI_076);
        BY_NAME.put("gui_077", GUI_077);
    }

    public static Sprite sprite(String name) {
        Sprite sprite = BY_NAME.get(Objects.requireNonNull(name, "name"));
        if (sprite == null) {
            throw new IllegalArgumentException("Unknown GUI sprite: " + name);
        }
        return sprite;
    }

    private WorkstationTextures() {
    }
}
