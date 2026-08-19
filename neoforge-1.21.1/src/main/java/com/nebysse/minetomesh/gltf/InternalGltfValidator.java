package com.nebysse.minetomesh.gltf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class InternalGltfValidator {
    private InternalGltfValidator() {
    }

    public static List<String> validate(JsonObject document, Path root) {
        List<String> errors = new ArrayList<>();
        Path normalizedRoot = root.toAbsolutePath().normalize();
        JsonArray buffers = array(document, "buffers");
        JsonArray bufferViews = array(document, "bufferViews");
        JsonArray accessors = array(document, "accessors");
        JsonArray materials = array(document, "materials");
        JsonArray meshes = array(document, "meshes");
        JsonArray nodes = array(document, "nodes");
        JsonArray scenes = array(document, "scenes");

        validateDefaultScene(document, scenes, errors);
        validateBuffers(buffers, normalizedRoot, errors);
        validateExternalUris(array(document, "images"), normalizedRoot, errors);

        for (int index = 0; index < bufferViews.size(); index++) {
            JsonObject view = bufferViews.get(index).getAsJsonObject();
            validateIndex(view, "buffer", buffers.size(), "bufferView " + index, errors);
            long offset = view.has("byteOffset") ? view.get("byteOffset").getAsLong() : 0L;
            if ((offset & 3L) != 0L) {
                errors.add("bufferView " + index + " byteOffset is not four-byte aligned");
            }
        }

        for (int index = 0; index < accessors.size(); index++) {
            JsonObject accessor = accessors.get(index).getAsJsonObject();
            validateIndex(accessor, "bufferView", bufferViews.size(), "accessor " + index, errors);
            validateFiniteArray(accessor, "min", "accessor " + index, errors);
            validateFiniteArray(accessor, "max", "accessor " + index, errors);
        }

        for (int meshIndex = 0; meshIndex < meshes.size(); meshIndex++) {
            JsonArray primitives = meshes.get(meshIndex).getAsJsonObject().getAsJsonArray("primitives");
            for (int primitiveIndex = 0; primitiveIndex < primitives.size(); primitiveIndex++) {
                JsonObject primitive = primitives.get(primitiveIndex).getAsJsonObject();
                String owner = "mesh " + meshIndex + " primitive " + primitiveIndex;
                validateIndex(primitive, "indices", accessors.size(), owner, errors);
                validateIndex(primitive, "material", materials.size(), owner, errors);
                JsonObject attributes = primitive.getAsJsonObject("attributes");
                for (String attribute : attributes.keySet()) {
                    int accessorIndex = attributes.get(attribute).getAsInt();
                    if (accessorIndex < 0 || accessorIndex >= accessors.size()) {
                        errors.add(owner + " attribute " + attribute + " index out of bounds");
                    }
                }
            }
        }

        for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
            JsonObject node = nodes.get(nodeIndex).getAsJsonObject();
            if (node.has("mesh")) {
                validateIndex(node, "mesh", meshes.size(), "node " + nodeIndex, errors);
            }
            if (node.has("children")) {
                validateIndexArray(node.getAsJsonArray("children"), nodes.size(),
                        "node " + nodeIndex + " child", errors);
            }
        }

        for (int sceneIndex = 0; sceneIndex < scenes.size(); sceneIndex++) {
            JsonObject scene = scenes.get(sceneIndex).getAsJsonObject();
            validateIndexArray(scene.getAsJsonArray("nodes"), nodes.size(),
                    "scene " + sceneIndex + " node", errors);
        }
        return List.copyOf(errors);
    }

    private static void validateDefaultScene(JsonObject document, JsonArray scenes, List<String> errors) {
        if (!document.has("scene")) {
            errors.add("Missing default scene index");
            return;
        }
        int scene = document.get("scene").getAsInt();
        if (scene < 0 || scene >= scenes.size()) {
            errors.add("Default scene index out of bounds");
        }
    }

    private static void validateBuffers(JsonArray buffers, Path root, List<String> errors) {
        for (int index = 0; index < buffers.size(); index++) {
            JsonObject buffer = buffers.get(index).getAsJsonObject();
            Path resource = resolveResource(buffer, root, errors);
            if (resource != null && Files.exists(resource)) {
                try {
                    long declared = buffer.get("byteLength").getAsLong();
                    long actual = Files.size(resource);
                    if (declared != actual) {
                        errors.add("Buffer " + index + " declared length " + declared
                                + " differs from file length " + actual);
                    }
                } catch (IOException exception) {
                    errors.add("Unable to read buffer " + index + ": " + exception.getMessage());
                }
            }
        }
    }

    private static void validateExternalUris(JsonArray resources, Path root, List<String> errors) {
        for (JsonElement resource : resources) {
            resolveResource(resource.getAsJsonObject(), root, errors);
        }
    }

    private static Path resolveResource(JsonObject object, Path root, List<String> errors) {
        if (!object.has("uri")) {
            errors.add("External resource is missing a URI");
            return null;
        }
        String uri = object.get("uri").getAsString();
        try {
            if (uri.indexOf('\\') >= 0) {
                errors.add("External resource URI must use forward slashes: " + uri);
                return null;
            }
            Path relative = Path.of(uri);
            Path resolved = root.resolve(relative).normalize();
            if (relative.isAbsolute() || !resolved.startsWith(root)) {
                errors.add("External resource escapes export root: " + uri);
                return null;
            }
            if (!Files.isRegularFile(resolved)) {
                errors.add("Missing external resource: " + uri);
                return null;
            }
            return resolved;
        } catch (InvalidPathException exception) {
            errors.add("Invalid external resource URI: " + uri);
            return null;
        }
    }

    private static void validateIndex(
            JsonObject owner,
            String property,
            int size,
            String ownerName,
            List<String> errors) {
        if (!owner.has(property)) {
            errors.add(ownerName + " is missing " + property);
            return;
        }
        int index = owner.get(property).getAsInt();
        if (index < 0 || index >= size) {
            errors.add(ownerName + " " + property + " index out of bounds");
        }
    }

    private static void validateIndexArray(
            JsonArray values, int size, String ownerName, List<String> errors) {
        for (JsonElement value : values) {
            int index = value.getAsInt();
            if (index < 0 || index >= size) {
                errors.add(ownerName + " index out of bounds");
            }
        }
    }

    private static void validateFiniteArray(
            JsonObject owner, String property, String ownerName, List<String> errors) {
        if (!owner.has(property)) {
            return;
        }
        for (JsonElement value : owner.getAsJsonArray(property)) {
            if (!Double.isFinite(value.getAsDouble())) {
                errors.add(ownerName + " " + property + " contains a non-finite value");
            }
        }
    }

    private static JsonArray array(JsonObject document, String property) {
        JsonArray array = document.getAsJsonArray(property);
        return array == null ? new JsonArray() : array;
    }
}
