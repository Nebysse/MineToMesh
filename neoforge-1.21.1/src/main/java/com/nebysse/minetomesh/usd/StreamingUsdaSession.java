package com.nebysse.minetomesh.usd;

import com.nebysse.minetomesh.capture.BlockPrimitiveRouter;
import com.nebysse.minetomesh.scene.CapturedNode;
import com.nebysse.minetomesh.scene.ChunkBatch;
import com.nebysse.minetomesh.scene.MaterialKey;
import com.nebysse.minetomesh.scene.PrimitiveData;
import com.nebysse.minetomesh.scene.PrimitiveMode;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class StreamingUsdaSession implements Closeable {
    private final Path root;
    private final Path usdaPath;
    private final String fileName;
    private final Map<String, Object> rootExtras;
    private final Map<CapturedNode.Kind, Fragment> categoryFragments =
            new EnumMap<>(CapturedNode.Kind.class);
    private final Map<MaterialKey, UsdaMeshSpool> blockMeshes = new LinkedHashMap<>();
    private final Map<MaterialKey, UsdaCurveSpool> blockCurves = new LinkedHashMap<>();
    private final Map<MaterialKey, UsdaMeshSpool> overlayMeshes = new LinkedHashMap<>();
    private final Map<MaterialKey, UsdaCurveSpool> overlayCurves = new LinkedHashMap<>();
    private final Set<MaterialKey> materials = new LinkedHashSet<>();
    private final Map<CapturedNode.Kind, Map<String, Integer>> usedNames =
            new EnumMap<>(CapturedNode.Kind.class);
    private long nodeCount;
    private long primitiveCount;
    private long faceCount;
    private long lineCount;
    private long spoolSequence;
    private boolean finished;
    private boolean closed;

    public StreamingUsdaSession(
            Path root, String name, Map<String, Object> rootExtras) throws IOException {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.fileName = Objects.requireNonNull(name, "name");
        this.rootExtras = Map.copyOf(Objects.requireNonNull(rootExtras, "rootExtras"));
        Files.createDirectories(this.root);
        usdaPath = this.root.resolve(name + ".usda");
        try {
            openCategory(CapturedNode.Kind.CHUNK, "chunks");
            openCategory(CapturedNode.Kind.ENTITY, "entities");
            openCategory(CapturedNode.Kind.PLACEHOLDER, "placeholders");
        } catch (IOException exception) {
            try {
                close();
            } catch (IOException cleanup) {
                exception.addSuppressed(cleanup);
            }
            throw exception;
        }
    }

    public void append(ChunkBatch batch) throws IOException {
        requireOpen();
        Objects.requireNonNull(batch, "batch");
        for (CapturedNode node : batch.nodes()) {
            switch (node.kind()) {
                case BLOCK_ENTITY -> appendGlobal(
                        node, blockMeshes, blockCurves, "block-entities");
                case OVERLAY -> appendOverlay(node);
                case CHUNK, ENTITY, PLACEHOLDER -> appendOrdinary(node);
            }
        }
    }

    private void appendOrdinary(CapturedNode node) throws IOException {
        Fragment fragment = categoryFragments.get(node.kind());
        String name = uniqueName(node.kind(), node.name());
        fragment.writer.write("        def Xform \"" + name + "\"\n        {\n");
        writeExtras(fragment.writer, node.extras(), "            ");
        Map<MaterialKey, UsdaMeshSpool> meshes = new LinkedHashMap<>();
        Map<MaterialKey, UsdaCurveSpool> curves = new LinkedHashMap<>();
        try {
            for (PrimitiveData primitive : node.primitives()) {
                materials.add(primitive.material());
                primitiveCount = Math.addExact(primitiveCount, 1L);
                if (isSurface(primitive.sourceMode())) {
                    faceCount = Math.addExact(faceCount, UsdaTopology.surface(
                            primitive.sourceMode(), primitive.streamVertexCounts(), node.name())
                            .faceVertexCounts().length);
                    mesh(meshes, primitive.material(), "local").append(primitive);
                } else {
                    lineCount = Math.addExact(lineCount, UsdaTopology.curves(
                            primitive.sourceMode(), primitive.streamVertexCounts(), node.name())
                            .curveVertexCounts().length);
                    curve(curves, primitive.material(), "local").append(primitive);
                }
            }
            for (Map.Entry<MaterialKey, UsdaMeshSpool> entry : meshes.entrySet()) {
                fragment.writer.write(entry.getValue().finish(
                        name + "_" + UsdaNames.material(entry.getKey())));
            }
            for (Map.Entry<MaterialKey, UsdaCurveSpool> entry : curves.entrySet()) {
                fragment.writer.write(entry.getValue().finish(
                        name + "_lines_" + UsdaNames.material(entry.getKey())));
            }
        } finally {
            closeAll(meshes.values());
            closeAll(curves.values());
        }
        fragment.writer.write("        }\n");
        nodeCount = Math.addExact(nodeCount, 1L);
    }

    private void appendGlobal(
            CapturedNode node,
            Map<MaterialKey, UsdaMeshSpool> meshes,
            Map<MaterialKey, UsdaCurveSpool> curves,
            String prefix) throws IOException {
        for (PrimitiveData primitive : node.primitives()) {
            materials.add(primitive.material());
            primitiveCount = Math.addExact(primitiveCount, 1L);
            if (isSurface(primitive.sourceMode())) {
                faceCount = Math.addExact(faceCount, UsdaTopology.surface(
                        primitive.sourceMode(), primitive.streamVertexCounts(), node.name())
                        .faceVertexCounts().length);
                mesh(meshes, primitive.material(), prefix).append(primitive);
            } else {
                lineCount = Math.addExact(lineCount, UsdaTopology.curves(
                        primitive.sourceMode(), primitive.streamVertexCounts(), node.name())
                        .curveVertexCounts().length);
                curve(curves, primitive.material(), prefix).append(primitive);
            }
        }
    }

    private void appendOverlay(CapturedNode node) throws IOException {
        if (!node.name().equals(BlockPrimitiveRouter.OVERLAY_OBJECT_NAME)) {
            throw new IOException("Unexpected overlay node " + node.name());
        }
        appendGlobal(node, overlayMeshes, overlayCurves, "overlay");
    }

    public OutputStatistics finish() throws IOException {
        requireOpen();
        IOException failure = null;
        try {
            closeCategoryWriters();
            try (BufferedWriter out = Files.newBufferedWriter(usdaPath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                writeHeader(out);
                writeCategory(out, "Chunks", categoryFragments.get(CapturedNode.Kind.CHUNK).path);
                writeBlockEntities(out);
                writeCategory(out, "Entities", categoryFragments.get(CapturedNode.Kind.ENTITY).path);
                writeCategory(out, "Placeholders",
                        categoryFragments.get(CapturedNode.Kind.PLACEHOLDER).path);
                writeOverlays(out);
                writeMaterials(out);
                out.write("}\n");
            }
            deleteCategoryFragments();
            finished = true;
            closed = true;
            return new OutputStatistics(nodeCount, primitiveCount, faceCount,
                    lineCount, usdaPath);
        } catch (IOException exception) {
            failure = exception;
            throw exception;
        } finally {
            if (failure != null) {
                try {
                    cleanupAll();
                } catch (IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
        }
    }

    private void writeHeader(BufferedWriter out) throws IOException {
        out.write("#usda 1.0\n(\n");
        out.write("    defaultPrim = \"MineToMesh\"\n");
        out.write("    metersPerUnit = 1\n");
        out.write("    upAxis = \"Y\"\n)\n\n");
        out.write("def Xform \"MineToMesh\"\n{\n");
        writeExtras(out, rootExtras, "    ");
    }

    private void writeCategory(BufferedWriter out, String name, Path fragment) throws IOException {
        out.write("    def Xform \"" + name + "\"\n    {\n");
        copy(fragment, out);
        out.write("    }\n");
    }

    private void writeBlockEntities(BufferedWriter out) throws IOException {
        out.write("    def Xform \"BlockEntities\"\n    {\n");
        for (Map.Entry<MaterialKey, UsdaMeshSpool> entry : sorted(blockMeshes)) {
            out.write(entry.getValue().finish("BlockEntities_" + UsdaNames.material(entry.getKey())));
            nodeCount = Math.addExact(nodeCount, 1L);
        }
        for (Map.Entry<MaterialKey, UsdaCurveSpool> entry : sorted(blockCurves)) {
            out.write(entry.getValue().finish("BlockEntities_lines_"
                    + UsdaNames.material(entry.getKey())));
            nodeCount = Math.addExact(nodeCount, 1L);
        }
        out.write("    }\n");
    }

    private void writeOverlays(BufferedWriter out) throws IOException {
        out.write("    def Xform \"Overlays\"\n    {\n");
        if (!overlayMeshes.isEmpty() || !overlayCurves.isEmpty()) {
            out.write("        def Xform \"selection_grass_side_overlay\"\n        {\n");
            for (Map.Entry<MaterialKey, UsdaMeshSpool> entry : sorted(overlayMeshes)) {
                out.write(entry.getValue().finish("Overlay_" + UsdaNames.material(entry.getKey())));
            }
            for (Map.Entry<MaterialKey, UsdaCurveSpool> entry : sorted(overlayCurves)) {
                out.write(entry.getValue().finish("Overlay_lines_"
                        + UsdaNames.material(entry.getKey())));
            }
            out.write("        }\n");
            nodeCount = Math.addExact(nodeCount, 1L);
        }
        out.write("    }\n");
    }

    private void writeMaterials(BufferedWriter out) throws IOException {
        out.write("    def Scope \"Materials\"\n    {\n");
        for (MaterialKey material : materials.stream()
                .sorted(Comparator.comparing(UsdaNames::material)).toList()) {
            out.write(UsdaMaterialWriter.fragment(material));
        }
        out.write("    }\n");
    }

    private UsdaMeshSpool mesh(
            Map<MaterialKey, UsdaMeshSpool> spools,
            MaterialKey material,
            String prefix) throws IOException {
        UsdaMeshSpool existing = spools.get(material);
        if (existing != null) return existing;
        UsdaMeshSpool created = new UsdaMeshSpool(
                root, fileName + "-" + prefix + "-" + spoolSequence++, material);
        spools.put(material, created);
        return created;
    }

    private UsdaCurveSpool curve(
            Map<MaterialKey, UsdaCurveSpool> spools,
            MaterialKey material,
            String prefix) throws IOException {
        UsdaCurveSpool existing = spools.get(material);
        if (existing != null) return existing;
        UsdaCurveSpool created = new UsdaCurveSpool(
                root, fileName + "-" + prefix + "-" + spoolSequence++, material);
        spools.put(material, created);
        return created;
    }

    private String uniqueName(CapturedNode.Kind kind, String source) {
        String base = UsdaNames.identifier(source);
        Map<String, Integer> names = usedNames.computeIfAbsent(kind, ignored -> new LinkedHashMap<>());
        int count = names.merge(base, 1, Integer::sum);
        return count == 1 ? base : base + "_" + count;
    }

    private void openCategory(CapturedNode.Kind kind, String suffix) throws IOException {
        Path path = root.resolve("." + fileName + "-" + suffix + ".usdapart");
        BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        categoryFragments.put(kind, new Fragment(path, writer));
    }

    private static boolean isSurface(PrimitiveMode mode) {
        return mode != PrimitiveMode.LINES && mode != PrimitiveMode.LINE_STRIP;
    }

    private static <T extends Closeable> void closeAll(Iterable<T> values) throws IOException {
        IOException failure = null;
        for (T value : values) {
            try { value.close(); } catch (IOException exception) {
                if (failure == null) failure = exception; else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    private static <T> List<Map.Entry<MaterialKey, T>> sorted(Map<MaterialKey, T> values) {
        return values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UsdaNames::material)))
                .toList();
    }

    private static void writeExtras(
            BufferedWriter out, Map<String, Object> extras, String indent) throws IOException {
        for (Map.Entry<String, Object> entry : extras.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            out.write(indent + "custom string minetomesh:"
                    + UsdaNames.identifier(entry.getKey()) + " = "
                    + UsdaText.quoted(String.valueOf(entry.getValue())) + "\n");
        }
    }

    private static void copy(Path path, BufferedWriter out) throws IOException {
        out.write(Files.readString(path, StandardCharsets.UTF_8));
    }

    private void closeCategoryWriters() throws IOException {
        IOException failure = null;
        for (Fragment fragment : categoryFragments.values()) {
            try { fragment.writer.close(); } catch (IOException exception) {
                if (failure == null) failure = exception; else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    private void deleteCategoryFragments() throws IOException {
        IOException failure = null;
        for (Fragment fragment : categoryFragments.values()) {
            try { Files.deleteIfExists(fragment.path); } catch (IOException exception) {
                if (failure == null) failure = exception; else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    private void cleanupAll() throws IOException {
        IOException failure = null;
        try { closeCategoryWriters(); } catch (IOException exception) { failure = exception; }
        for (Iterable<? extends Closeable> spools : List.of(
                blockMeshes.values(), blockCurves.values(), overlayMeshes.values(), overlayCurves.values())) {
            try { closeAll(spools); } catch (IOException exception) {
                if (failure == null) failure = exception; else failure.addSuppressed(exception);
            }
        }
        try { deleteCategoryFragments(); } catch (IOException exception) {
            if (failure == null) failure = exception; else failure.addSuppressed(exception);
        }
        try { Files.deleteIfExists(usdaPath); } catch (IOException exception) {
            if (failure == null) failure = exception; else failure.addSuppressed(exception);
        }
        if (failure != null) throw failure;
    }

    private void requireOpen() {
        if (closed || finished) throw new IllegalStateException("Streaming USDA session is closed");
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        cleanupAll();
    }

    public record OutputStatistics(
            long nodeCount,
            long primitiveCount,
            long faceCount,
            long lineCount,
            Path usdaPath) {
    }

    private record Fragment(Path path, BufferedWriter writer) {
    }

}
