package com.onecuber.mcgltf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentationPolicyTest {
    @Test
    void readmeDocumentsReleaseAndWorkstation() throws Exception {
        String readme = Files.readString(
                projectRoot().resolve("README.md"), StandardCharsets.UTF_8);
        List<String> required = List.of(
                "mcgltf-0.3.2.jar",
                "客户端和服务端",
                "区域导出工作台",
                "关闭 GUI 会取消",
                "铁锭",
                "玻璃板",
                "红石",
                "制图台",
                "/mcgltf");
        for (String fragment : required) {
            assertTrue(readme.contains(fragment),
                    "README must mention: " + fragment);
        }
    }

    @Test
    void manualMatrixCoversWorkstationChecks() throws Exception {
        String matrix = Files.readString(
                projectRoot().resolve("docs/testing/manual-client-matrix.md"),
                StandardCharsets.UTF_8);
        List<String> required = List.of(
                "区域导出工作台",
                "GUI Scale 2/3/4",
                "两名玩家",
                "深度遮挡",
                "方块拆除",
                "关闭即取消",
                "专用服务器",
                "Create",
                "女仆",
                "Blender");
        for (String fragment : required) {
            assertTrue(matrix.contains(fragment),
                    "manual matrix must cover: " + fragment);
        }
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir"))
                .getParent()
                .getParent();
    }
}
