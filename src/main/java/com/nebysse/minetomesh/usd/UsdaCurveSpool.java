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
import java.util.List;
import java.util.Objects;

final class UsdaCurveSpool implements Closeable {
    private final MaterialKey material;
    private final Path pointsPath;
    private final Path countsPath;
    private final Path uvPath;
    private final Path colorPath;
    private final BufferedWriter points;
    private final BufferedWriter counts;
    private final BufferedWriter uvs;
    private final BufferedWriter colors;
    private boolean firstPoint = true;
    private boolean firstCount = true;
    private boolean firstUv = true;
    private boolean firstColor = true;
    private long curveCount;
    private long vertexCount;
    private long primitiveCount;
    private boolean closed;

    UsdaCurveSpool(Path root, String key, MaterialKey material) throws IOException {
        this.material = Objects.requireNonNull(material, "material");
        pointsPath = root.resolve("." + key + "-curve-points.usdapart");
        countsPath = root.resolve("." + key + "-curve-counts.usdapart");
        uvPath = root.resolve("." + key + "-curve-uvs.usdapart");
        colorPath = root.resolve("." + key + "-curve-colors.usdapart");
        points = writer(pointsPath);
        counts = writer(countsPath);
        uvs = writer(uvPath);
        colors = writer(colorPath);
    }

    void append(PrimitiveData primitive) throws IOException {
        requireOpen();
        UsdaTopology.Curves topology = UsdaTopology.curves(
                primitive.sourceMode(), primitive.streamVertexCounts(), "usda");
        List<Vertex> vertices = primitive.vertices();
        for (int count : topology.curveVertexCounts()) {
            firstCount = scalar(counts, firstCount, Integer.toString(count));
            curveCount = Math.addExact(curveCount, 1L);
        }
        for (int index : topology.vertexIndices()) {
            Vertex vertex = vertices.get(index);
            firstPoint = tuple(points, firstPoint, vertex.position());
            firstUv = tuple(uvs, firstUv, vertex.uv());
            firstColor = tuple(colors, firstColor, vertex.color());
            vertexCount = Math.addExact(vertexCount, 1L);
        }
        primitiveCount = Math.addExact(primitiveCount, 1L);
    }

    String finish(String name) throws IOException {
        closeWriters();
        if (sumCounts(countsPath) != vertexCount) {
            throw new IOException("USDA curve counts do not cover their vertices");
        }
        String indent = "            ";
        String child = "                ";
        String out = indent + "def BasisCurves \"" + UsdaNames.identifier(name) + "\"\n"
                + indent + "(\n"
                + child + "prepend apiSchemas = [\"MaterialBindingAPI\"]\n"
                + indent + ")\n"
                + indent + "{\n"
                + child + "uniform token type = \"linear\"\n"
                + child + "uniform token wrap = \"nonperiodic\"\n"
                + child + "point3f[] points = [" + read(pointsPath) + "]\n"
                + child + "int[] curveVertexCounts = [" + read(countsPath) + "]\n"
                + child + "texCoord2f[] primvars:st = [" + read(uvPath)
                + "] (interpolation = \"vertex\")\n"
                + child + "color4f[] primvars:minetomeshTint = [" + read(colorPath)
                + "] (interpolation = \"vertex\")\n"
                + child + "rel material:binding = </MineToMesh/Materials/"
                + UsdaNames.material(material) + ">\n"
                + indent + "}\n";
        deleteFiles();
        return out;
    }

    long curveCount() {
        return curveCount;
    }

    long primitiveCount() {
        return primitiveCount;
    }

    private static BufferedWriter writer(Path path) throws IOException {
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
        if (!first) writer.write(", ");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static long sumCounts(Path path) throws IOException {
        String text = read(path).trim();
        if (text.isEmpty()) return 0;
        long sum = 0;
        for (String part : text.split(", ")) sum = Math.addExact(sum, Long.parseLong(part));
        return sum;
    }

    private void closeWriters() throws IOException {
        if (closed) return;
        closed = true;
        IOException failure = null;
        for (BufferedWriter writer : List.of(points, counts, uvs, colors)) {
            try { writer.close(); } catch (IOException exception) {
                if (failure == null) failure = exception; else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    private void deleteFiles() throws IOException {
        IOException failure = null;
        for (Path path : List.of(pointsPath, countsPath, uvPath, colorPath)) {
            try { Files.deleteIfExists(path); } catch (IOException exception) {
                if (failure == null) failure = exception; else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("USDA curve spool is closed");
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        try { closeWriters(); } catch (IOException exception) { failure = exception; }
        try { deleteFiles(); } catch (IOException exception) {
            if (failure == null) failure = exception; else failure.addSuppressed(exception);
        }
        if (failure != null) throw failure;
    }
}
