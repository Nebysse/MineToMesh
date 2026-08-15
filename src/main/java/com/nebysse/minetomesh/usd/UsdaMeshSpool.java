package com.nebysse.minetomesh.usd;

import com.nebysse.minetomesh.scene.ColorRgba;
import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.PrimitiveData;
import com.nebysse.minetomesh.scene.Vec2f;
import com.nebysse.minetomesh.scene.Vec3f;
import com.nebysse.minetomesh.scene.Vertex;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class UsdaMeshSpool implements Closeable {
    private final MaterialKey material;
    private final List<Path> paths = new ArrayList<>();
    private final BufferedWriter points;
    private final BufferedWriter counts;
    private final BufferedWriter indices;
    private final BufferedWriter normals;
    private final BufferedWriter uvs;
    private final BufferedWriter colors;
    private long pointCount;
    private long faceCount;
    private long faceVertexCount;
    private long primitiveCount;
    private boolean firstPoint = true;
    private boolean firstCount = true;
    private boolean firstIndex = true;
    private boolean firstNormal = true;
    private boolean firstUv = true;
    private boolean firstColor = true;
    private boolean closed;

    UsdaMeshSpool(Path root, String key, MaterialKey material) throws IOException {
        this.material = Objects.requireNonNull(material, "material");
        points = writer(root, key, "points");
        counts = writer(root, key, "counts");
        indices = writer(root, key, "indices");
        normals = writer(root, key, "normals");
        uvs = writer(root, key, "uvs");
        colors = writer(root, key, "colors");
    }

    void append(PrimitiveData primitive) throws IOException {
        requireOpen();
        Objects.requireNonNull(primitive, "primitive");
        UsdaTopology.Surface topology = UsdaTopology.surface(
                primitive.sourceMode(), primitive.streamVertexCounts(), "usda");
        List<Vertex> vertices = primitive.vertices();
        for (Vertex vertex : vertices) {
            firstPoint = tuple(points, firstPoint, vertex.position());
        }
        for (int count : topology.faceVertexCounts()) {
            firstCount = scalar(counts, firstCount, Integer.toString(count));
            faceCount = Math.addExact(faceCount, 1L);
        }
        for (int localIndex : topology.faceVertexIndices()) {
            if (localIndex < 0 || localIndex >= vertices.size()) {
                throw new IOException("USDA topology index is outside its source primitive");
            }
            firstIndex = scalar(indices, firstIndex,
                    Long.toString(Math.addExact(pointCount, localIndex)));
            Vertex vertex = vertices.get(localIndex);
            firstNormal = tuple(normals, firstNormal, vertex.normal());
            firstUv = tuple(uvs, firstUv, vertex.uv());
            firstColor = tuple(colors, firstColor, vertex.color());
            faceVertexCount = Math.addExact(faceVertexCount, 1L);
        }
        pointCount = Math.addExact(pointCount, vertices.size());
        primitiveCount = Math.addExact(primitiveCount, 1L);
    }

    String finish(String meshName) throws IOException {
        closeWriters();
        long countSum = sumCounts(paths.get(1));
        long indexCount = countValues(paths.get(2));
        if (countSum != faceVertexCount || indexCount != faceVertexCount) {
            throw new IOException("USDA mesh attribute counts are inconsistent");
        }
        String indent = "            ";
        String child = "                ";
        StringBuilder out = new StringBuilder();
        out.append(indent).append("def Mesh \"").append(UsdaNames.identifier(meshName))
                .append("\"\n").append(indent).append("{\n")
                .append(child).append("uniform token subdivisionScheme = \"none\"\n")
                .append(child).append("uniform bool doubleSided = ")
                .append(material.doubleSided()).append("\n")
                .append(child).append("point3f[] points = [")
                .append(read(paths.get(0))).append("]\n")
                .append(child).append("int[] faceVertexCounts = [")
                .append(read(paths.get(1))).append("]\n")
                .append(child).append("int[] faceVertexIndices = [")
                .append(read(paths.get(2))).append("]\n")
                .append(child).append("normal3f[] normals = [")
                .append(read(paths.get(3))).append("] (\n")
                .append(child).append("    interpolation = \"faceVarying\"\n")
                .append(child).append(")\n")
                .append(child).append("texCoord2f[] primvars:st = [")
                .append(read(paths.get(4))).append("] (\n")
                .append(child).append("    interpolation = \"faceVarying\"\n")
                .append(child).append(")\n")
                .append(child).append("color4f[] primvars:minetomeshTint = [")
                .append(read(paths.get(5))).append("] (\n")
                .append(child).append("    interpolation = \"faceVarying\"\n")
                .append(child).append(")\n")
                .append(child).append("rel material:binding = </MineToMesh/Materials/")
                .append(UsdaNames.material(material)).append(">\n")
                .append(indent).append("}\n");
        deletePaths();
        return out.toString();
    }

    long faceCount() {
        return faceCount;
    }

    long primitiveCount() {
        return primitiveCount;
    }

    private BufferedWriter writer(Path root, String key, String attribute) throws IOException {
        Path path = root.resolve("." + key + "-" + attribute + ".usdapart");
        paths.add(path);
        return Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static boolean tuple(BufferedWriter writer, boolean first, Vec3f value)
            throws IOException {
        separator(writer, first);
        writer.write("(" + UsdaText.number(value.x()) + ", "
                + UsdaText.number(value.y()) + ", " + UsdaText.number(value.z()) + ")");
        return false;
    }

    private static boolean tuple(BufferedWriter writer, boolean first, Vec2f value)
            throws IOException {
        separator(writer, first);
        writer.write("(" + UsdaText.number(value.x()) + ", "
                + UsdaText.number(1.0F - value.y()) + ")");
        return false;
    }

    private static boolean tuple(BufferedWriter writer, boolean first, ColorRgba value)
            throws IOException {
        separator(writer, first);
        writer.write("(" + UsdaText.number(value.red() / 255.0F) + ", "
                + UsdaText.number(value.green() / 255.0F) + ", "
                + UsdaText.number(value.blue() / 255.0F) + ", "
                + UsdaText.number(value.alpha() / 255.0F) + ")");
        return false;
    }

    private static boolean scalar(BufferedWriter writer, boolean first, String value)
            throws IOException {
        separator(writer, first);
        writer.write(value);
        return false;
    }

    private static void separator(BufferedWriter writer, boolean first) throws IOException {
        if (!first) {
            writer.write(", ");
        }
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static long countValues(Path path) throws IOException {
        String value = read(path).trim();
        return value.isEmpty() ? 0 : value.split(", ").length;
    }

    private static long sumCounts(Path path) throws IOException {
        String value = read(path).trim();
        if (value.isEmpty()) {
            return 0;
        }
        long sum = 0;
        for (String part : value.split(", ")) {
            sum = Math.addExact(sum, Long.parseLong(part));
        }
        return sum;
    }

    private void closeWriters() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException failure = null;
        for (BufferedWriter writer : List.of(points, counts, indices, normals, uvs, colors)) {
            try {
                writer.close();
            } catch (IOException exception) {
                if (failure == null) failure = exception; else failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("USDA mesh spool is closed");
        }
    }

    private void deletePaths() throws IOException {
        IOException failure = null;
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                if (failure == null) failure = exception; else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        try {
            closeWriters();
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            deletePaths();
        } catch (IOException exception) {
            if (failure == null) failure = exception; else failure.addSuppressed(exception);
        }
        if (failure != null) throw failure;
    }
}
