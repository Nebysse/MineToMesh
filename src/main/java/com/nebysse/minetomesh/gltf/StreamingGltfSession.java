package com.nebysse.minetomesh.gltf;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.nebysse.minetomesh.scene.CapturedNode;
import com.nebysse.minetomesh.scene.ChunkBatch;
import com.nebysse.minetomesh.scene.ColorRgba;
import com.nebysse.minetomesh.scene.PrimitiveData;
import com.nebysse.minetomesh.scene.TopologyConverter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class StreamingGltfSession implements Closeable {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path root;
    private final Path gltfPath;
    private final BinaryBufferWriter binaryWriter;
    private final GltfDocumentBuilder documentBuilder;
    private long nodeCount;
    private long primitiveCount;
    private boolean finished;
    private boolean closed;

    public StreamingGltfSession(Path root, String name, Map<String, Object> rootExtras) throws IOException {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        Objects.requireNonNull(name, "name");
        Files.createDirectories(this.root);
        Path binaryPath = this.root.resolve(name + ".bin");
        this.gltfPath = this.root.resolve(name + ".gltf");
        OutputStream output = Files.newOutputStream(binaryPath,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        this.binaryWriter = new BinaryBufferWriter(output);
        this.documentBuilder = new GltfDocumentBuilder(name + ".bin", rootExtras);
    }

    public void append(ChunkBatch batch) throws IOException {
        requireOpen();
        Objects.requireNonNull(batch, "batch");
        for (CapturedNode node : batch.nodes()) {
            List<WrittenPrimitive> written = new ArrayList<>(node.primitives().size());
            for (PrimitiveData primitive : node.primitives()) {
                written.add(write(primitive, node.name()));
                primitiveCount = Math.addExact(primitiveCount, 1L);
            }
            if (!written.isEmpty()
                    && documentBuilder.addNode(node, written)) {
                nodeCount = Math.addExact(nodeCount, 1L);
            }
        }
    }

    public OutputStatistics finish() throws IOException {
        requireOpen();
        binaryWriter.close();
        long binaryByteLength = binaryWriter.byteLength();
        JsonObject document = documentBuilder.finish(binaryByteLength);
        Files.writeString(gltfPath, GSON.toJson(document), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        finished = true;
        closed = true;
        return new OutputStatistics(nodeCount, primitiveCount, binaryByteLength, gltfPath);
    }

    private WrittenPrimitive write(PrimitiveData primitive, String objectId) throws IOException {
        TopologyConverter.ConvertedTopology topology =
                TopologyConverter.convert(primitive, objectId);
        BinaryBufferWriter.Segment positions =
                binaryWriter.writePositions(primitive.vertices());
        BinaryBufferWriter.Segment normals =
                binaryWriter.writeNormals(primitive.vertices());
        BinaryBufferWriter.Segment texCoords =
                binaryWriter.writeTexCoords(primitive.vertices());
        Optional<BinaryBufferWriter.Segment> colors = primitive.vertices().stream()
                .allMatch(vertex -> vertex.color().equals(ColorRgba.WHITE))
                ? Optional.empty()
                : Optional.of(binaryWriter.writeColors(primitive.vertices()));
        return new WrittenPrimitive(
                positions,
                normals,
                texCoords,
                colors,
                binaryWriter.writeIndices(topology.indices()),
                topology.gltfMode(),
                primitive.material());
    }

    private void requireOpen() {
        if (closed || finished) {
            throw new IllegalStateException("Streaming glTF session is closed");
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            binaryWriter.close();
        }
    }

    public record OutputStatistics(
            long nodeCount,
            long primitiveCount,
            long binaryByteLength,
            Path gltfPath) {
    }
}
