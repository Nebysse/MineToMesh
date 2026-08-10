package com.onecuber.mcgltf.output;

import com.onecuber.mcgltf.gltf.StreamingGltfSession;
import com.onecuber.mcgltf.obj.StreamingObjSession;
import com.onecuber.mcgltf.scene.ChunkBatch;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class StreamingSceneSession implements Closeable {
    private final StreamingGltfSession gltf;
    private final StreamingObjSession obj;
    private boolean closed;

    public StreamingSceneSession(
            Path root,
            String name,
            Map<String, Object> rootExtras) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(rootExtras, "rootExtras");
        StreamingGltfSession openedGltf = new StreamingGltfSession(root, name, rootExtras);
        try {
            obj = new StreamingObjSession(root, name);
            gltf = openedGltf;
        } catch (IOException exception) {
            openedGltf.close();
            throw exception;
        }
    }

    public void append(ChunkBatch batch) throws IOException {
        requireOpen();
        gltf.append(Objects.requireNonNull(batch, "batch"));
        obj.append(batch);
    }

    public OutputStatistics finish() throws IOException {
        requireOpen();
        StreamingGltfSession.OutputStatistics gltfOutput = gltf.finish();
        StreamingObjSession.OutputStatistics objOutput = obj.finish();
        closed = true;
        return new OutputStatistics(gltfOutput, objOutput);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Streaming scene session is closed");
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException first = null;
        try {
            gltf.close();
        } catch (IOException exception) {
            first = exception;
        }
        try {
            obj.close();
        } catch (IOException exception) {
            if (first == null) {
                first = exception;
            } else {
                first.addSuppressed(exception);
            }
        }
        if (first != null) {
            throw first;
        }
    }

    public record OutputStatistics(
            StreamingGltfSession.OutputStatistics gltf,
            StreamingObjSession.OutputStatistics obj) {
        public OutputStatistics {
            Objects.requireNonNull(gltf, "gltf");
            Objects.requireNonNull(obj, "obj");
        }
    }
}
