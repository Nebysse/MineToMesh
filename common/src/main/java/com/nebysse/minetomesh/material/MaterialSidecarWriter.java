package com.nebysse.minetomesh.material;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.nebysse.minetomesh.capture.RenderTypeDescriptor;
import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.texture.TextureImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MaterialSidecarWriter {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private MaterialSidecarWriter() {
    }

    public static Path write(Path transactionRoot, MaterialRecord record) throws IOException {
        Path root = Objects.requireNonNull(transactionRoot, "transactionRoot")
                .toAbsolutePath().normalize();
        Objects.requireNonNull(record, "record");
        Path directory = root.resolve("materials").normalize();
        if (!directory.startsWith(root)) {
            throw new IOException("Material directory escapes transaction root");
        }
        Files.createDirectories(directory);
        String slug = record.material().texture().sourceId()
                .replaceAll("[^A-Za-z0-9._-]+", "_");
        Path output = directory.resolve(String.format("%04d-%s.json", record.gltfMaterialIndex(), slug));
        Files.writeString(output, GSON.toJson(toJson(record)), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return output;
    }

    private static JsonObject toJson(MaterialRecord record) {
        JsonObject json = new JsonObject();
        json.addProperty("schemaVersion", 1);
        json.addProperty("gltfMaterialIndex", record.gltfMaterialIndex());
        json.addProperty("sourceTexture", record.material().texture().sourceId());
        json.addProperty("exportedRelativeUri", record.material().texture().outputPath());
        json.addProperty("renderType", record.renderType().name());

        JsonObject alpha = new JsonObject();
        alpha.addProperty("mode", record.material().alphaMode().name());
        if (record.material().alphaCutoff().isPresent()) {
            alpha.addProperty("cutoff", record.material().alphaCutoff().orElseThrow());
        } else {
            alpha.add("cutoff", JsonNull.INSTANCE);
        }
        json.add("alpha", alpha);
        json.addProperty("doubleSided", record.material().doubleSided());
        json.addProperty("emissive", record.material().emissive());
        json.addProperty("blendSemantic", record.material().blendSemantic().name());
        json.addProperty("sampler", record.material().samplerMode().name());

        if (record.animation().isPresent()) {
            TextureImage.AnimationInfo animation = record.animation().orElseThrow();
            JsonObject animationJson = new JsonObject();
            animationJson.addProperty("frameWidth", animation.frameWidth());
            animationJson.addProperty("frameHeight", animation.frameHeight());
            animationJson.add("frameOrder", integers(animation.frameOrder()));
            animationJson.add("frameTimes", integers(animation.frameTimes()));
            animationJson.addProperty("interpolate", animation.interpolate());
            json.add("animation", animationJson);
        } else {
            json.add("animation", JsonNull.INSTANCE);
        }

        JsonArray degradation = new JsonArray();
        record.degradationCodes().forEach(degradation::add);
        json.add("degradationCodes", degradation);
        return json;
    }

    private static JsonArray integers(List<Integer> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    public record MaterialRecord(
            int gltfMaterialIndex,
            MaterialKey material,
            RenderTypeDescriptor renderType,
            Optional<TextureImage.AnimationInfo> animation,
            List<String> degradationCodes) {
        public MaterialRecord {
            if (gltfMaterialIndex < 0) {
                throw new IllegalArgumentException("glTF material index must not be negative");
            }
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(renderType, "renderType");
            Objects.requireNonNull(animation, "animation");
            degradationCodes = List.copyOf(degradationCodes);
        }
    }
}
