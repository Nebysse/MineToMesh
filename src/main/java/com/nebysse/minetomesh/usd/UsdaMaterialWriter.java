package com.nebysse.minetomesh.usd;

import com.nebysse.minetomesh.scene.MaterialKey;
import java.util.Objects;

public final class UsdaMaterialWriter {
    private UsdaMaterialWriter() {
    }

    public static String fragment(MaterialKey material) {
        Objects.requireNonNull(material, "material");
        String name = UsdaNames.material(material);
        String base = "/MineToMesh/Materials/" + name;
        StringBuilder out = new StringBuilder();
        out.append("        def Material \"").append(name).append("\"\n        {\n")
                .append("            token outputs:surface.connect = <")
                .append(base).append("/Preview.outputs:surface>\n")
                .append("            custom string minetomesh:samplerMode = ")
                .append(UsdaText.quoted(material.samplerMode().name())).append("\n")
                .append("            custom string minetomesh:blendSemantic = ")
                .append(UsdaText.quoted(material.blendSemantic().name())).append("\n\n")
                .append("            def Shader \"Preview\"\n            {\n")
                .append("                uniform token info:id = \"UsdPreviewSurface\"\n")
                .append("                color3f inputs:diffuseColor.connect = <")
                .append(base).append("/Texture.outputs:rgb>\n");
        if (material.alphaMode() != MaterialKey.AlphaMode.OPAQUE) {
            out.append("                float inputs:opacity.connect = <")
                    .append(base).append("/Texture.outputs:a>\n");
        } else {
            out.append("                float inputs:opacity = 1\n");
        }
        material.alphaCutoff().ifPresent(cutoff -> out.append(
                "                float inputs:opacityThreshold = ")
                .append(UsdaText.number(cutoff)).append("\n"));
        if (material.emissive()) {
            out.append("                color3f inputs:emissiveColor.connect = <")
                    .append(base).append("/Texture.outputs:rgb>\n");
        }
        out.append("                float inputs:roughness = 1\n")
                .append("                float inputs:metallic = 0\n")
                .append("                token outputs:surface\n            }\n\n")
                .append("            def Shader \"Texture\"\n            {\n")
                .append("                uniform token info:id = \"UsdUVTexture\"\n")
                .append("                asset inputs:file = ")
                .append(UsdaText.asset(material.texture().outputPath())).append("\n")
                .append("                token inputs:sourceColorSpace = \"sRGB\"\n")
                .append("                float2 inputs:st.connect = <")
                .append(base).append("/ReadSt.outputs:result>\n")
                .append("                float4 inputs:scale.connect = <")
                .append(base).append("/ReadTint.outputs:result>\n")
                .append("                float3 outputs:rgb\n")
                .append("                float outputs:a\n            }\n\n")
                .append("            def Shader \"ReadSt\"\n            {\n")
                .append("                uniform token info:id = \"UsdPrimvarReader_float2\"\n")
                .append("                string inputs:varname = \"st\"\n")
                .append("                float2 outputs:result\n            }\n\n")
                .append("            def Shader \"ReadTint\"\n            {\n")
                .append("                uniform token info:id = \"UsdPrimvarReader_float4\"\n")
                .append("                string inputs:varname = \"minetomeshTint\"\n")
                .append("                float4 inputs:fallback = (1, 1, 1, 1)\n")
                .append("                float4 outputs:result\n            }\n")
                .append("        }\n");
        return out.toString();
    }
}
