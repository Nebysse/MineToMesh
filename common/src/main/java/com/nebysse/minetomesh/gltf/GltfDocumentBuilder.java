package com.nebysse.minetomesh.gltf;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nebysse.minetomesh.MineToMeshInfo;
import com.nebysse.minetomesh.capture.BlockPrimitiveRouter;
import com.nebysse.minetomesh.scene.CapturedNode;
import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.TextureKey;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GltfDocumentBuilder {
    private static final Gson GSON = new Gson();
    private static final int CHUNKS_ROOT = 0;
    private static final int BLOCK_ENTITIES_ROOT = 1;
    private static final int ENTITIES_ROOT = 2;
    private static final int PLACEHOLDERS_ROOT = 3;
    private static final int OVERLAYS_ROOT = 4;

    private final String bufferUri;
    private final Map<String, Object> rootExtras;
    private final JsonArray bufferViews = new JsonArray();
    private final JsonArray accessors = new JsonArray();
    private final JsonArray samplers = new JsonArray();
    private final JsonArray images = new JsonArray();
    private final JsonArray textures = new JsonArray();
    private final JsonArray materials = new JsonArray();
    private final JsonArray meshes = new JsonArray();
    private final JsonArray nodes = new JsonArray();
    private final Map<MaterialKey.SamplerMode, Integer> samplerIndices =
            new EnumMap<>(MaterialKey.SamplerMode.class);
    private final Map<TextureKey, Integer> imageIndices = new LinkedHashMap<>();
    private final Map<TextureBinding, Integer> textureIndices = new LinkedHashMap<>();
    private final Map<MaterialKey, Integer> materialIndices = new LinkedHashMap<>();
    private final Map<String, CapturedNode.Kind> logicalKinds = new LinkedHashMap<>();
    private final Map<String, MergedNode> overlayNodes = new LinkedHashMap<>();
    private final Map<MaterialKey, MergedNode> blockEntityMaterialNodes =
            new LinkedHashMap<>();
    private boolean finished;

    public GltfDocumentBuilder(String bufferUri, Map<String, Object> rootExtras) {
        this.bufferUri = Objects.requireNonNull(bufferUri, "bufferUri");
        this.rootExtras = new LinkedHashMap<>(Objects.requireNonNull(rootExtras, "rootExtras"));
        addHierarchyRoot("Chunks");
        addHierarchyRoot("BlockEntities");
        addHierarchyRoot("Entities");
        addHierarchyRoot("Placeholders");
        addHierarchyRoot("Overlays");
    }

    public int addNode(CapturedNode capturedNode, List<WrittenPrimitive> writtenPrimitives) {
        requireOpen();
        Objects.requireNonNull(capturedNode, "capturedNode");
        List<WrittenPrimitive> written = List.copyOf(writtenPrimitives);
        if (written.size() != capturedNode.primitives().size()) {
            throw new IllegalArgumentException("Written primitive count must match captured primitive count");
        }
        if (written.isEmpty()) {
            return 0;
        }
        if (capturedNode.kind() == CapturedNode.Kind.BLOCK_ENTITY) {
            return addBlockEntityPrimitives(written);
        }

        CapturedNode.Kind existingKind = logicalKinds.putIfAbsent(
                capturedNode.name(), capturedNode.kind());
        if (existingKind != null && existingKind != capturedNode.kind()) {
            throw new IllegalArgumentException(
                    "Node name is already bound to " + existingKind + ": " + capturedNode.name());
        }

        if (capturedNode.kind() == CapturedNode.Kind.OVERLAY
                || capturedNode.name().equals(
                        BlockPrimitiveRouter.MERGED_CHUNKS_OBJECT_NAME)) {
            MergedNode existing = overlayNodes.get(capturedNode.name());
            if (existing != null && !existing.extras().equals(capturedNode.extras())) {
                throw new IllegalArgumentException(
                        "Overlay fragments must have identical extras: " + capturedNode.name());
            }
            JsonArray primitiveJson = primitives(written);
            if (existing != null) {
                primitiveJson.forEach(existing.primitives()::add);
                return 0;
            }
            int meshIndex = addMesh(capturedNode.name(), primitiveJson);
            int nodeIndex = addCapturedNode(capturedNode, meshIndex);
            overlayNodes.put(capturedNode.name(), new MergedNode(
                    primitiveJson, Map.copyOf(capturedNode.extras()), nodeIndex, meshIndex));
            return 1;
        }

        int meshIndex = addMesh(capturedNode.name(), primitives(written));
        addCapturedNode(capturedNode, meshIndex);
        return 1;
    }

    private int addBlockEntityPrimitives(List<WrittenPrimitive> written) {
        int createdNodes = 0;
        for (WrittenPrimitive primitive : written) {
            MaterialKey material = primitive.material();
            JsonObject primitiveJson = addPrimitive(primitive);
            MergedNode existing = blockEntityMaterialNodes.get(material);
            if (existing != null) {
                existing.primitives().add(primitiveJson);
                continue;
            }

            String name = String.format(
                    "BlockEntities/material_%04d", blockEntityMaterialNodes.size());
            JsonArray primitiveJsonArray = new JsonArray();
            primitiveJsonArray.add(primitiveJson);
            int meshIndex = addMesh(name, primitiveJsonArray);
            CapturedNode mergedNode = new CapturedNode(
                    name,
                    CapturedNode.Kind.BLOCK_ENTITY,
                    List.of(),
                    Map.of(
                            "mergePolicy", "GLOBAL_MATERIAL",
                            "materialSourceId", material.texture().sourceId()));
            int nodeIndex = addCapturedNode(mergedNode, meshIndex);
            logicalKinds.put(name, CapturedNode.Kind.BLOCK_ENTITY);
            blockEntityMaterialNodes.put(material, new MergedNode(
                    primitiveJsonArray, mergedNode.extras(), nodeIndex, meshIndex));
            createdNodes = Math.addExact(createdNodes, 1);
        }
        return createdNodes;
    }

    private JsonArray primitives(List<WrittenPrimitive> written) {
        JsonArray primitiveJson = new JsonArray();
        for (WrittenPrimitive primitive : written) {
            primitiveJson.add(addPrimitive(primitive));
        }
        return primitiveJson;
    }

    private int addMesh(String name, JsonArray primitiveJson) {
        JsonObject mesh = new JsonObject();
        mesh.addProperty("name", name);
        mesh.add("primitives", primitiveJson);
        int meshIndex = meshes.size();
        meshes.add(mesh);
        return meshIndex;
    }

    private int addCapturedNode(CapturedNode capturedNode, int meshIndex) {
        JsonObject node = new JsonObject();
        node.addProperty("name", capturedNode.name());
        node.addProperty("mesh", meshIndex);
        if (!capturedNode.extras().isEmpty()) {
            node.add("extras", GSON.toJsonTree(capturedNode.extras()));
        }
        int nodeIndex = nodes.size();
        nodes.add(node);
        hierarchyRoot(capturedNode.kind()).getAsJsonArray("children").add(nodeIndex);
        return nodeIndex;
    }

    public JsonObject finish(long binaryByteLength) {
        requireOpen();
        if (binaryByteLength < 0) {
            throw new IllegalArgumentException("Binary byte length must not be negative");
        }
        finished = true;

        JsonObject document = new JsonObject();
        JsonObject asset = new JsonObject();
        asset.addProperty("version", "2.0");
        asset.addProperty("generator",
                MineToMeshInfo.DISPLAY_NAME + " " + MineToMeshInfo.CORE_VERSION);
        document.add("asset", asset);
        document.addProperty("scene", 0);

        JsonObject scene = new JsonObject();
        scene.addProperty("name", "Minecraft Export");
        JsonArray rootNodes = new JsonArray();
        rootNodes.add(CHUNKS_ROOT);
        rootNodes.add(BLOCK_ENTITIES_ROOT);
        rootNodes.add(ENTITIES_ROOT);
        rootNodes.add(PLACEHOLDERS_ROOT);
        rootNodes.add(OVERLAYS_ROOT);
        scene.add("nodes", rootNodes);
        JsonArray scenes = new JsonArray();
        scenes.add(scene);
        document.add("scenes", scenes);
        document.add("nodes", nodes);
        document.add("meshes", meshes);
        document.add("accessors", accessors);
        document.add("bufferViews", bufferViews);
        document.add("samplers", samplers);
        document.add("images", images);
        document.add("textures", textures);
        document.add("materials", materials);

        JsonObject buffer = new JsonObject();
        buffer.addProperty("uri", bufferUri);
        buffer.addProperty("byteLength", binaryByteLength);
        JsonArray buffers = new JsonArray();
        buffers.add(buffer);
        document.add("buffers", buffers);
        if (!rootExtras.isEmpty()) {
            document.add("extras", GSON.toJsonTree(rootExtras));
        }
        return document;
    }

    private JsonObject addPrimitive(WrittenPrimitive primitive) {
        int positionAccessor = addAccessor(primitive.positions(), "VEC3", false);
        int normalAccessor = addAccessor(primitive.normals(), "VEC3", false);
        int uvAccessor = addAccessor(primitive.texCoords(), "VEC2", false);
        int indexAccessor = addAccessor(primitive.indices(), "SCALAR", false);

        JsonObject attributes = new JsonObject();
        attributes.addProperty("POSITION", positionAccessor);
        attributes.addProperty("NORMAL", normalAccessor);
        attributes.addProperty("TEXCOORD_0", uvAccessor);
        primitive.colors().ifPresent(segment -> attributes.addProperty(
                "COLOR_0", addAccessor(segment, "VEC4", true)));

        JsonObject json = new JsonObject();
        json.add("attributes", attributes);
        json.addProperty("indices", indexAccessor);
        json.addProperty("material", materialIndex(primitive.material()));
        json.addProperty("mode", primitive.gltfMode());
        return json;
    }

    private int addAccessor(BinaryBufferWriter.Segment segment, String type, boolean normalized) {
        JsonObject view = new JsonObject();
        view.addProperty("buffer", 0);
        view.addProperty("byteOffset", segment.byteOffset());
        view.addProperty("byteLength", segment.byteLength());
        view.addProperty("target", segment.target());
        int viewIndex = bufferViews.size();
        bufferViews.add(view);

        JsonObject accessor = new JsonObject();
        accessor.addProperty("bufferView", viewIndex);
        accessor.addProperty("byteOffset", 0);
        accessor.addProperty("componentType", segment.componentType());
        accessor.addProperty("count", segment.elementCount());
        accessor.addProperty("type", type);
        if (normalized) {
            accessor.addProperty("normalized", true);
        }
        segment.min().ifPresent(values -> accessor.add("min", floatArray(values)));
        segment.max().ifPresent(values -> accessor.add("max", floatArray(values)));
        int accessorIndex = accessors.size();
        accessors.add(accessor);
        return accessorIndex;
    }

    private int materialIndex(MaterialKey key) {
        return materialIndices.computeIfAbsent(key, material -> {
            int textureIndex = textureIndex(material.texture(), material.samplerMode());
            JsonObject textureRef = new JsonObject();
            textureRef.addProperty("index", textureIndex);

            JsonObject pbr = new JsonObject();
            pbr.add("baseColorTexture", textureRef);
            pbr.addProperty("metallicFactor", 0.0F);
            pbr.addProperty("roughnessFactor", 1.0F);

            JsonObject json = new JsonObject();
            json.addProperty("name", material.texture().sourceId());
            json.add("pbrMetallicRoughness", pbr);
            json.addProperty("alphaMode", material.alphaMode().name());
            material.alphaCutoff().ifPresent(value -> json.addProperty("alphaCutoff", value));
            json.addProperty("doubleSided", material.doubleSided());
            if (material.emissive()) {
                JsonObject emissiveTexture = new JsonObject();
                emissiveTexture.addProperty("index", textureIndex);
                json.add("emissiveTexture", emissiveTexture);
                JsonArray factor = new JsonArray();
                factor.add(1.0F);
                factor.add(1.0F);
                factor.add(1.0F);
                json.add("emissiveFactor", factor);
            }
            JsonObject extras = new JsonObject();
            extras.addProperty("blendSemantic", material.blendSemantic().name());
            json.add("extras", extras);
            int index = materials.size();
            materials.add(json);
            return index;
        });
    }

    private int textureIndex(TextureKey textureKey, MaterialKey.SamplerMode samplerMode) {
        TextureBinding binding = new TextureBinding(textureKey, samplerMode);
        return textureIndices.computeIfAbsent(binding, ignored -> {
            JsonObject texture = new JsonObject();
            texture.addProperty("source", imageIndex(textureKey));
            texture.addProperty("sampler", samplerIndex(samplerMode));
            int index = textures.size();
            textures.add(texture);
            return index;
        });
    }

    private int imageIndex(TextureKey key) {
        return imageIndices.computeIfAbsent(key, textureKey -> {
            JsonObject image = new JsonObject();
            image.addProperty("name", textureKey.sourceId());
            image.addProperty("uri", textureKey.outputPath());
            int index = images.size();
            images.add(image);
            return index;
        });
    }

    private int samplerIndex(MaterialKey.SamplerMode mode) {
        return samplerIndices.computeIfAbsent(mode, samplerMode -> {
            JsonObject sampler = new JsonObject();
            sampler.addProperty("magFilter", 9728);
            sampler.addProperty("minFilter",
                    samplerMode == MaterialKey.SamplerMode.NEAREST_MIPMAP ? 9984 : 9728);
            sampler.addProperty("wrapS", 10497);
            sampler.addProperty("wrapT", 10497);
            int index = samplers.size();
            samplers.add(sampler);
            return index;
        });
    }

    private void addHierarchyRoot(String name) {
        JsonObject node = new JsonObject();
        node.addProperty("name", name);
        node.add("children", new JsonArray());
        nodes.add(node);
    }

    private JsonObject hierarchyRoot(CapturedNode.Kind kind) {
        return nodes.get(switch (kind) {
            case CHUNK -> CHUNKS_ROOT;
            case BLOCK_ENTITY -> BLOCK_ENTITIES_ROOT;
            case ENTITY -> ENTITIES_ROOT;
            case PLACEHOLDER -> PLACEHOLDERS_ROOT;
            case OVERLAY -> OVERLAYS_ROOT;
        }).getAsJsonObject();
    }

    private static JsonArray floatArray(float[] values) {
        JsonArray array = new JsonArray();
        for (float value : values) {
            array.add(value);
        }
        return array;
    }

    private void requireOpen() {
        if (finished) {
            throw new IllegalStateException("glTF document is already finished");
        }
    }

    private record MergedNode(
            JsonArray primitives,
            Map<String, Object> extras,
            int nodeIndex,
            int meshIndex) {
    }

    private record TextureBinding(TextureKey texture, MaterialKey.SamplerMode sampler) {
    }
}
