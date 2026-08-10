package com.onecuber.mcgltf.material;

import com.onecuber.mcgltf.capture.RenderTypeDescriptor;
import com.onecuber.mcgltf.scene.MaterialKey;
import com.onecuber.mcgltf.scene.TextureKey;
import java.util.Objects;

public final class MaterialResolver {
    private MaterialResolver() {
    }

    public static MaterialKey resolve(RenderTypeDescriptor descriptor, TextureKey texture) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(texture, "texture");
        return new MaterialKey(
                texture,
                descriptor.alphaMode(),
                descriptor.alphaCutoff(),
                !descriptor.cull(),
                descriptor.emissive(),
                descriptor.blendSemantic(),
                descriptor.mipmap()
                        ? MaterialKey.SamplerMode.NEAREST_MIPMAP
                        : MaterialKey.SamplerMode.NEAREST);
    }

    public static MaterialKey resolve(RenderTypeDescriptor descriptor) {
        String sourceId = descriptor.textureResourceId().orElse("mcgltf:missing_texture");
        TextureKey texture = ResourceTexturePaths.keyFor(sourceId);
        return resolve(descriptor, texture);
    }

    private static final class ResourceTexturePaths {
        private static TextureKey keyFor(String sourceId) {
            int separator = sourceId.indexOf(':');
            String namespace = separator >= 0 ? sourceId.substring(0, separator) : "minecraft";
            String path = separator >= 0 ? sourceId.substring(separator + 1) : sourceId;
            if (path.startsWith("textures/")) {
                path = path.substring("textures/".length());
            }
            if (path.endsWith(".png")) {
                path = path.substring(0, path.length() - 4);
            }
            return new TextureKey(TextureKey.Kind.RESOURCE, sourceId,
                    "textures/" + namespace + "/" + path + ".png");
        }
    }
}
