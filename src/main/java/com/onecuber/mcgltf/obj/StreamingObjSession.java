package com.onecuber.mcgltf.obj;

import com.onecuber.mcgltf.capture.BlockPrimitiveRouter;
import com.onecuber.mcgltf.scene.CapturedNode;
import com.onecuber.mcgltf.scene.ChunkBatch;
import com.onecuber.mcgltf.scene.MaterialKey;
import com.onecuber.mcgltf.scene.PrimitiveData;
import com.onecuber.mcgltf.scene.Vertex;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class StreamingObjSession implements Closeable {
    private static final String OVERLAY_FAILURE = "GLOBAL_OVERLAY_SPOOL_FAILED";

    private final Path objPath;
    private final Path mtlPath;
    private final Path overlayFragmentPath;
    private final BufferedWriter writer;
    private final Map<MaterialKey, String> materialNames = new LinkedHashMap<>();
    private BufferedWriter overlayWriter;
    private long vertexCount;
    private long nodeCount;
    private long primitiveCount;
    private long faceCount;
    private long lineCount;
    private boolean overlayWritten;
    private boolean finished;
    private boolean closed;

    public StreamingObjSession(Path root, String name) throws IOException {
        Path normalizedRoot = Objects.requireNonNull(root, "root")
                .toAbsolutePath().normalize();
        String fileName = Objects.requireNonNull(name, "name");
        Files.createDirectories(normalizedRoot);
        objPath = normalizedRoot.resolve(fileName + ".obj");
        mtlPath = normalizedRoot.resolve(fileName + ".mtl");
        overlayFragmentPath = normalizedRoot.resolve(
                "." + fileName + "-grass-overlay.objpart");
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
            if (node.kind() == CapturedNode.Kind.OVERLAY) {
                writeOverlay(node);
            } else {
                writeOrdinaryNode(node);
            }
        }
    }

    private void writeOrdinaryNode(CapturedNode node) throws IOException {
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
            writeVertices(writer, primitive.vertices());
            int base = Math.toIntExact(vertexCount + 1L);
            writeAbsoluteTopology(writer, faces, lines, base);
            vertexCount = Math.addExact(vertexCount, primitive.vertices().size());
            primitiveCount = Math.addExact(primitiveCount, 1L);
            wrotePrimitive = true;
        }
        if (wrotePrimitive) {
            nodeCount = Math.addExact(nodeCount, 1L);
        }
    }

    private void writeOverlay(CapturedNode node) throws IOException {
        if (!node.name().equals(BlockPrimitiveRouter.OVERLAY_OBJECT_NAME)) {
            throw new IOException(OVERLAY_FAILURE + ": unexpected overlay node " + node.name());
        }
        try {
            boolean wroteFragment = false;
            for (PrimitiveData primitive : node.primitives()) {
                List<int[]> faces = ObjTopologyConverter.faces(primitive);
                List<int[]> lines = ObjTopologyConverter.lines(primitive);
                if (faces.isEmpty() && lines.isEmpty()) {
                    continue;
                }
                BufferedWriter fragment = overlayWriter();
                String objectName = ObjNames.sanitize(node.name());
                String materialName = materialName(primitive.material());
                fragment.write("g " + objectName + "_" + materialName + "\n");
                fragment.write("usemtl " + materialName + "\n");
                writeVertices(fragment, primitive.vertices());
                writeRelativeTopology(fragment, faces, lines, primitive.vertices().size());
                primitiveCount = Math.addExact(primitiveCount, 1L);
                wroteFragment = true;
            }
            if (wroteFragment && !overlayWritten) {
                overlayWritten = true;
                nodeCount = Math.addExact(nodeCount, 1L);
            }
        } catch (IOException exception) {
            throw overlayFailure(exception);
        }
    }

    private BufferedWriter overlayWriter() throws IOException {
        if (overlayWriter == null) {
            overlayWriter = Files.newBufferedWriter(
                    overlayFragmentPath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        return overlayWriter;
    }

    private void writeAbsoluteTopology(
            BufferedWriter target,
            List<int[]> faces,
            List<int[]> lines,
            int base) throws IOException {
        for (int[] face : faces) {
            target.write("f");
            for (int index : face) {
                int absolute = Math.addExact(base, index);
                target.write(" " + absolute + "/" + absolute + "/" + absolute);
            }
            target.write("\n");
            faceCount = Math.addExact(faceCount, 1L);
        }
        for (int[] line : lines) {
            target.write("l");
            for (int index : line) {
                target.write(" " + Math.addExact(base, index));
            }
            target.write("\n");
            lineCount = Math.addExact(lineCount, 1L);
        }
    }

    private void writeRelativeTopology(
            BufferedWriter target,
            List<int[]> faces,
            List<int[]> lines,
            int vertexSize) throws IOException {
        for (int[] face : faces) {
            target.write("f");
            for (int index : face) {
                int relative = index - vertexSize;
                target.write(" " + relative + "/" + relative + "/" + relative);
            }
            target.write("\n");
            faceCount = Math.addExact(faceCount, 1L);
        }
        for (int[] line : lines) {
            target.write("l");
            for (int index : line) {
                target.write(" " + (index - vertexSize));
            }
            target.write("\n");
            lineCount = Math.addExact(lineCount, 1L);
        }
    }

    public OutputStatistics finish() throws IOException {
        requireOpen();
        try {
            closeOverlayWriter();
            if (overlayWritten) {
                writer.write("\no " + ObjNames.sanitize(
                        BlockPrimitiveRouter.OVERLAY_OBJECT_NAME) + "\n");
                copyOverlayFragment();
            }
            writer.close();
            writeMaterials();
            finished = true;
            closed = true;
            return new OutputStatistics(
                    nodeCount, primitiveCount, faceCount, lineCount, objPath, mtlPath);
        } catch (IOException exception) {
            if (overlayWritten || Files.exists(overlayFragmentPath)) {
                throw overlayFailure(exception);
            }
            throw exception;
        } finally {
            Files.deleteIfExists(overlayFragmentPath);
        }
    }

    private void copyOverlayFragment() throws IOException {
        try (Reader reader = new BufferedReader(Files.newBufferedReader(
                overlayFragmentPath, StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            for (int read; (read = reader.read(buffer)) >= 0; ) {
                writer.write(buffer, 0, read);
            }
        }
    }

    private void closeOverlayWriter() throws IOException {
        if (overlayWriter != null) {
            BufferedWriter fragment = overlayWriter;
            overlayWriter = null;
            fragment.close();
        }
    }

    private static void writeVertices(
            BufferedWriter target,
            List<Vertex> vertices) throws IOException {
        for (Vertex vertex : vertices) {
            target.write("v " + vertex.position().x() + " "
                    + vertex.position().y() + " " + vertex.position().z() + " "
                    + vertex.color().red() / 255.0F + " "
                    + vertex.color().green() / 255.0F + " "
                    + vertex.color().blue() / 255.0F + "\n");
        }
        for (Vertex vertex : vertices) {
            target.write("vt " + vertex.uv().x() + " " + vertex.uv().y() + "\n");
        }
        for (Vertex vertex : vertices) {
            target.write("vn " + vertex.normal().x() + " "
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

    private static IOException overlayFailure(IOException cause) {
        if (cause.getMessage() != null && cause.getMessage().startsWith(OVERLAY_FAILURE)) {
            return cause;
        }
        return new IOException(OVERLAY_FAILURE + ": "
                + (cause.getMessage() == null ? cause.getClass().getName() : cause.getMessage()), cause);
    }

    private void requireOpen() {
        if (closed || finished) {
            throw new IllegalStateException("Streaming OBJ session is closed");
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException failure = null;
        try {
            closeOverlayWriter();
        } catch (IOException exception) {
            failure = overlayFailure(exception);
        }
        try {
            writer.close();
        } catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        try {
            Files.deleteIfExists(overlayFragmentPath);
        } catch (IOException exception) {
            IOException wrapped = overlayFailure(exception);
            if (failure == null) {
                failure = wrapped;
            } else {
                failure.addSuppressed(wrapped);
            }
        }
        if (failure != null) {
            throw failure;
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
