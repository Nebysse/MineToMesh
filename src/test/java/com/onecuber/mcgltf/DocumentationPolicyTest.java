package com.onecuber.mcgltf;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentationPolicyTest {
    @Test
    void readmeDocumentsTheWandReleaseAndMigration() throws Exception {
        String readme = read("README.md");
        for (String fragment : List.of(
                "mcgltf-0.5.0.jar",
                "客户端和服务端",
                "导出魔杖",
                "Shift+右键",
                "Shift+左键空气",
                "紫水晶碎片",
                "铜锭",
                "权限等级 2",
                "不兼容旧存档",
                "实际 Atlas UV",
                "selection/grass_side_overlay",
                "Overlays",
                "Unicode",
                "/mcgltf")) {
            assertTrue(readme.contains(fragment),
                    "README must mention: " + fragment);
        }
        assertFalse(readme.contains("制作区域导出工作台"));
        assertFalse(readme.contains("放置区域导出工作台"));
    }

    @Test
    void manualMatrixCoversTheExactWandClosure() throws Exception {
        String matrix = read("docs/testing/manual-client-matrix.md");
        for (String fragment : List.of(
                "无裂纹左键",
                "容器安全右键",
                "Shift+右键方块",
                "Shift+右键空气",
                "Shift+左键空气清除",
                "跨维度拒绝",
                "双魔杖隔离",
                "Overlay 隐藏与恢复",
                "移动魔杖使菜单失效",
                "重连授权生命周期",
                "普通玩家",
                "管理员",
                "单人模式",
                "glTF",
                "OBJ",
                "Blender",
                "Create 连纹",
                "草侧 Overlay 对象",
                "英文 E 输入",
                "中文输入法",
                "关闭后恢复")) {
            assertTrue(matrix.contains(fragment),
                    "manual matrix must cover: " + fragment);
        }
    }

    private static String read(String relative) throws Exception {
        return Files.readString(projectRoot().resolve(relative),
                StandardCharsets.UTF_8);
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().getParent();
    }
}
