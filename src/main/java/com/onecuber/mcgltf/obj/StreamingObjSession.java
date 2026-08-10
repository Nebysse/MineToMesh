package com.onecuber.mcgltf.obj;

import com.onecuber.mcgltf.scene.CapturedNode;
import com.onecuber.mcgltf.scene.ChunkBatch;
import com.onecuber.mcgltf.scene.MaterialKey;
import com.onecuber.mcgltf.scene.PrimitiveData;
import com.onecuber.mcgltf.scene.Vertex;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class StreamingObjSession implements Closeable {
    private final Path objPath;
    private final Path mtlPath;
    private final BufferedWriter writer;
    private final Map<MaterialKey, String> materialNames = new LinkedHashMap<>();
    private long vertexCount;
    private long nodeCount;
    private long primitiveCount;
    private long faceCount;
    private long lineCount;
    private boolean finished;
    private boolean closed;

    public StreamingObjSession(Path root, String name) throws IOException {
        Path normalizedRoot = Objects.requireNonNull(root, "root")
                .toAbsolutePath().normalize();
        String fileName = Objects.requireNonNull(name, "name");
        Files.createDirectories(normalizedRoot);
        objPath = normalizedRoot.resolve(fileName + ".obj");
        mtlPath = normalizedRoot.resolve(fileName + ".mtl");
        writer = Files.newBufferedWriter(objPath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        writer.write("mtllib " + fileName + ".mtl\n");
    }

    public void append(ChunkBatch batch) throws IOException {
        requireOpen();
        Objects.requireNonNull(batch, "batch");
        for (CapturedNode node : batch.nodes()) {
            if (node.primitives().isEmpty()) {
                continue;
            }
            String objectName = ObjNames.sanitize(node.name());
            writer.write("\no " + objectName + "\n");
            boolean wrotePrimitive = false;
            for (PrimitiveData primitive : node.primitives()) {
                List<int[]> faces = ObjTopologyConverter.faces(primitive);
                List<int[]> lines = ObjTopologyConverter.lines(primitive);
                if (faces.isEmpty() && lines.isEmpty()) {
                    continue;
                }
                String materialName = materialName(primitive.material());
                writer.write("g " + objectName + "_" + materialName + "\n");
                writer.write("usemtl " + materialName + "\n");
                writeVertices(primitive.vertices());
                int base = Math.toIntExact(vertexCount + 1L);
                for (int[] face : faces) {
                    writer.write("f");
                    for (int index : face) {
                        int absolute = Math.addExact(base, index);
                        writer.write(" " + absolute + "/" + absolute + "/" + absolute);
                    }
                    writer.write("\n");
                    faceCount = Math.addExact(faceCount, 1L);
                }
                for (int[] line : lines) {
                    writer.write("l");
                    for (int index : line) {
                        writer.write(" " + Math.addExact(base, index));
                    }
                    writer.write("\n");
                    lineCount = Math.addExact(lineCount, 1L);
                }
                vertexCount = Math.addExact(vertexCount, primitive.vertices().size());
                primitiveCount = Math.addExact(primitiveCount, 1L);
                wrotePrimitive = true;
            }
            if (wrotePrimitive) {
                nodeCount = Math.addExact(nodeCount, 1L);
            }
        }
    }

    public OutputStatistics finish() throws IOException {
        requireOpen();
        writer.close();
        writeMaterials();
        finished = true;
        closed = true;
        return new OutputStatistics(
                nodeCount, primitiveCount, faceCount, lineCount, objPath, mtlPath);
    }

    private void writeVertices(List<Vertex> vertices) throws IOException {
        for (Vertex vertex : vertices) {
            writer.write("v " + vertex.position().x() + " "
                    + vertex.position().y() + " " + vertex.position().z() + "\n");
        }
        for (Vertex vertex : vertices) {
            writer.write("vt " + vertex.uv().x() + " " + vertex.uv().y() + "\n");
        }
        for (Vertex vertex : vertices) {
            writer.write("vn " + vertex.normal().x() + " "
                    + vertex.normal().y() + " " + vertex.normal().z() + "\n");
        }
    }

    private String materialName(MaterialKey material) {
        return materialNames.computeIfAbsent(material,
                ignored -> String.format("m%04d", materialNames.size()));
    }

    private void writeMaterials() throws IOException {
        try (BufferedWriter mtl = Files.newBufferedWriter(
                mtlPath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            for (Map.Entry<MaterialKey, String> entry : materialNames.entrySet()) {
                MaterialKey material = entry.getKey();
                String texturePath = material.texture().outputPath().replace('\\', '/');
                mtl.write("newmtl " + entry.getValue() + "\n");
                mtl.write("Kd 1.0 1.0 1.0\n");
                mtl.write(material.emissive()
                        ? "Ke 1.0 1.0 1.0\n" : "Ke 0.0 0.0 0.0\n");
                mtl.write("illum 2\n");
                mtl.write("d 1.0\n");
                mtl.write("map_Kd " + texturePath + "\n");
                if (material.alphaMode() != MaterialKey.AlphaMode.OPAQUE) {
                    mtl.write("map_d " + texturePath + "\n");
                }
                mtl.write("\n");
            }
        }
    }

    private void requireOpen() {
        if (closed || finished) {
            throw new IllegalStateException("Streaming OBJ session is closed");
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            writer.close();
        }
    }

    public record OutputStatistics(
            long nodeCount,
            long primitiveCount,
            long faceCount,
            long lineCount,
            Path objPath,
            Path mtlPath) {
    }
}
