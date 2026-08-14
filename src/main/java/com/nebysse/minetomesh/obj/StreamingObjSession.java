package com.nebysse.minetomesh.obj;

import com.nebysse.minetomesh.capture.BlockPrimitiveRouter;
import com.nebysse.minetomesh.scene.CapturedNode;
import com.nebysse.minetomesh.scene.ChunkBatch;
import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.PrimitiveData;
import com.nebysse.minetomesh.scene.Vertex;
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
    private static final String BLOCK_ENTITY_FAILURE =
            "GLOBAL_BLOCK_ENTITY_SPOOL_FAILED";

    private final Path objPath;
    private final Path mtlPath;
    private final Path overlayFragmentPath;
    private final String fileName;
    private final BufferedWriter writer;
    private final Map<MaterialKey, String> materialNames = new LinkedHashMap<>();
    private final Map<MaterialKey, BlockEntitySpool> blockEntitySpools =
            new LinkedHashMap<>();
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
        fileName = Objects.requireNonNull(name, "name");
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
            } else if (node.kind() == CapturedNode.Kind.BLOCK_ENTITY) {
                writeBlockEntityNode(node);
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

    private void writeBlockEntityNode(CapturedNode node) throws IOException {
        try {
            for (PrimitiveData primitive : node.primitives()) {
                List<int[]> faces = ObjTopologyConverter.faces(primitive);
                List<int[]> lines = ObjTopologyConverter.lines(primitive);
                if (faces.isEmpty() && lines.isEmpty()) {
                    continue;
                }
                BlockEntitySpool spool = blockEntitySpool(primitive.material());
                writeVertices(spool.writer, primitive.vertices());
                writeRelativeTopology(
                        spool.writer, faces, lines, primitive.vertices().size());
                primitiveCount = Math.addExact(primitiveCount, 1L);
            }
        } catch (IOException exception) {
            throw blockEntityFailure(exception);
        }
    }

    private BlockEntitySpool blockEntitySpool(MaterialKey material) throws IOException {
        BlockEntitySpool existing = blockEntitySpools.get(material);
        if (existing != null) {
            return existing;
        }
        String materialName = materialName(material);
        Path path = objPath.getParent().resolve(
                "." + fileName + "-block-entities-" + materialName + ".objpart");
        try {
            BufferedWriter fragment = Files.newBufferedWriter(
                    path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            BlockEntitySpool created = new BlockEntitySpool(
                    materialName, path, fragment);
            blockEntitySpools.put(material, created);
            return created;
        } catch (IOException exception) {
            throw blockEntityFailure(exception);
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
        OutputStatistics result = null;
        IOException failure = null;
        try {
            IOException blockEntityCloseFailure = closeBlockEntityWriters();
            if (blockEntityCloseFailure != null) {
                throw blockEntityCloseFailure;
            }
            closeOverlayWriter();
            writeBlockEntityObjects();
            if (overlayWritten) {
                writer.write("\no " + ObjNames.sanitize(
                        BlockPrimitiveRouter.OVERLAY_OBJECT_NAME) + "\n");
                copyFragment(overlayFragmentPath);
            }
            writer.close();
            writeMaterials();
            finished = true;
            closed = true;
            result = new OutputStatistics(
                    nodeCount, primitiveCount, faceCount, lineCount, objPath, mtlPath);
        } catch (IOException exception) {
            failure = classifyFinishFailure(exception);
        } finally {
            IOException cleanupFailure = deleteSpools();
            if (failure == null) {
                failure = cleanupFailure;
            } else if (cleanupFailure != null) {
                failure.addSuppressed(cleanupFailure);
            }
        }
        if (failure != null) {
            throw failure;
        }
        return Objects.requireNonNull(result, "result");
    }

    private void writeBlockEntityObjects() throws IOException {
        try {
            for (BlockEntitySpool spool : blockEntitySpools.values()) {
                String objectName = "BlockEntities_" + spool.materialName;
                writer.write("\no " + objectName + "\n");
                writer.write("g " + objectName + "\n");
                writer.write("usemtl " + spool.materialName + "\n");
                copyFragment(spool.path);
                nodeCount = Math.addExact(nodeCount, 1L);
            }
        } catch (IOException exception) {
            throw blockEntityFailure(exception);
        }
    }

    private void copyFragment(Path path) throws IOException {
        try (Reader reader = new BufferedReader(Files.newBufferedReader(
                path, StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            for (int read; (read = reader.read(buffer)) >= 0; ) {
                writer.write(buffer, 0, read);
            }
        }
    }

    private IOException closeBlockEntityWriters() {
        IOException failure = null;
        for (BlockEntitySpool spool : blockEntitySpools.values()) {
            if (spool.writer == null) {
                continue;
            }
            BufferedWriter fragment = spool.writer;
            spool.writer = null;
            try {
                fragment.close();
            } catch (IOException exception) {
                failure = combine(failure, blockEntityFailure(exception));
            }
        }
        return failure;
    }

    private void closeOverlayWriter() throws IOException {
        if (overlayWriter != null) {
            BufferedWriter fragment = overlayWriter;
            overlayWriter = null;
            fragment.close();
        }
    }

    private IOException deleteSpools() {
        IOException failure = null;
        try {
            Files.deleteIfExists(overlayFragmentPath);
        } catch (IOException exception) {
            failure = combine(failure, overlayFailure(exception));
        }
        for (BlockEntitySpool spool : blockEntitySpools.values()) {
            try {
                Files.deleteIfExists(spool.path);
            } catch (IOException exception) {
                failure = combine(failure, blockEntityFailure(exception));
            }
        }
        return failure;
    }

    private IOException classifyFinishFailure(IOException exception) {
        String message = exception.getMessage();
        if (message != null && (message.startsWith(OVERLAY_FAILURE)
                || message.startsWith(BLOCK_ENTITY_FAILURE))) {
            return exception;
        }
        if (overlayWritten || Files.exists(overlayFragmentPath)) {
            return overlayFailure(exception);
        }
        return exception;
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

    private static IOException blockEntityFailure(IOException cause) {
        if (cause.getMessage() != null && cause.getMessage().startsWith(BLOCK_ENTITY_FAILURE)) {
            return cause;
        }
        return new IOException(BLOCK_ENTITY_FAILURE + ": "
                + (cause.getMessage() == null ? cause.getClass().getName() : cause.getMessage()), cause);
    }

    private static IOException combine(IOException existing, IOException additional) {
        if (additional == null) {
            return existing;
        }
        if (existing == null) {
            return additional;
        }
        existing.addSuppressed(additional);
        return existing;
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
        IOException failure = closeBlockEntityWriters();
        try {
            closeOverlayWriter();
        } catch (IOException exception) {
            failure = combine(failure, overlayFailure(exception));
        }
        try {
            writer.close();
        } catch (IOException exception) {
            failure = combine(failure, exception);
        }
        failure = combine(failure, deleteSpools());
        if (failure != null) {
            throw failure;
        }
    }

    private static final class BlockEntitySpool {
        private final String materialName;
        private final Path path;
        private BufferedWriter writer;

        private BlockEntitySpool(
                String materialName, Path path, BufferedWriter writer) {
            this.materialName = Objects.requireNonNull(materialName, "materialName");
            this.path = Objects.requireNonNull(path, "path");
            this.writer = Objects.requireNonNull(writer, "writer");
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
