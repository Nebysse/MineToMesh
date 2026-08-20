package com.nebysse.minetomesh.world;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

public record ExportPlan(
        Selection selection,
        List<SectionWork> sections,
        List<MissingChunk> missingChunks) {
    public ExportPlan {
        Objects.requireNonNull(selection, "selection");
        sections = sections.stream()
                .sorted(Comparator.comparing(SectionWork::section))
                .toList();
        missingChunks = List.copyOf(new TreeSet<>(missingChunks));
    }

    public long totalWorkItems() {
        return sections.size();
    }

    public ChunkRange chunkRange() {
        return ChunkRange.from(selection);
    }

    public ChunkBatchCursor chunkCursor() {
        return chunkRange().cursor();
    }

    public record SectionWork(
            ChunkSectionRef section,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ) {
        public SectionWork {
            Objects.requireNonNull(section, "section");
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("Section work bounds must be normalized");
            }
        }

        public long positionCount() {
            long x = (long) maxX - minX + 1L;
            long y = (long) maxY - minY + 1L;
            long z = (long) maxZ - minZ + 1L;
            return Math.multiplyExact(Math.multiplyExact(x, y), z);
        }
    }

    public record MissingChunk(int chunkX, int chunkZ) implements Comparable<MissingChunk> {
        @Override
        public int compareTo(MissingChunk other) {
            int x = Integer.compare(chunkX, other.chunkX);
            return x != 0 ? x : Integer.compare(chunkZ, other.chunkZ);
        }
    }
}
