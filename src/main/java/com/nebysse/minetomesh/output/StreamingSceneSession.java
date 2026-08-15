package com.nebysse.minetomesh.output;

import com.nebysse.minetomesh.gltf.StreamingGltfSession;
import com.nebysse.minetomesh.scene.ChunkBatch;
import com.nebysse.minetomesh.usd.StreamingUsdaSession;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class StreamingSceneSession implements Closeable {
    private final StreamingGltfSession gltf;
    private final StreamingUsdaSession usda;
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
            usda = new StreamingUsdaSession(root, name, rootExtras);
            gltf = openedGltf;
        } catch (IOException exception) {
            openedGltf.close();
            throw exception;
        }
    }

    public void append(ChunkBatch batch) throws IOException {
        requireOpen();
        gltf.append(Objects.requireNonNull(batch, "batch"));
        usda.append(batch);
    }

    public OutputStatistics finish() throws IOException {
        requireOpen();
        StreamingGltfSession.OutputStatistics gltfOutput = gltf.finish();
        StreamingUsdaSession.OutputStatistics usdaOutput = usda.finish();
        closed = true;
        return new OutputStatistics(gltfOutput, usdaOutput);
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
            usda.close();
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
            StreamingUsdaSession.OutputStatistics usda) {
        public OutputStatistics {
            Objects.requireNonNull(gltf, "gltf");
            Objects.requireNonNull(usda, "usda");
        }
    }
}
