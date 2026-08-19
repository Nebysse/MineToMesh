package com.nebysse.minetomesh.scene;

public record BatchCounters(
        long scannedPositions,
        long renderedBlocks,
        long renderedFluids,
        long blockEntities,
        long entities,
        long materials,
        long textures,
        long triangles,
        long placeholders) {
    public static final BatchCounters ZERO = new BatchCounters(0, 0, 0, 0, 0, 0, 0, 0, 0);

    public BatchCounters {
        if (scannedPositions < 0 || renderedBlocks < 0 || renderedFluids < 0
                || blockEntities < 0 || entities < 0 || materials < 0
                || textures < 0 || triangles < 0 || placeholders < 0) {
            throw new IllegalArgumentException("Batch counters must not be negative");
        }
    }

    public BatchCounters plus(BatchCounters other) {
        return new BatchCounters(
                Math.addExact(scannedPositions, other.scannedPositions),
                Math.addExact(renderedBlocks, other.renderedBlocks),
                Math.addExact(renderedFluids, other.renderedFluids),
                Math.addExact(blockEntities, other.blockEntities),
                Math.addExact(entities, other.entities),
                Math.addExact(materials, other.materials),
                Math.addExact(textures, other.textures),
                Math.addExact(triangles, other.triangles),
                Math.addExact(placeholders, other.placeholders));
    }
}
