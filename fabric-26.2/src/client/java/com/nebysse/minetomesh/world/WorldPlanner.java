package com.nebysse.minetomesh.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;

public final class WorldPlanner {
    public ExportPlan plan(ClientLevel level, Selection selection) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(selection, "selection");
        String dimension = level.dimension().identifier().toString();
        if (!dimension.equals(selection.min().dimension())) {
            throw new IllegalArgumentException("Selection dimension does not match the active client level");
        }

        int minY = Math.max(selection.min().y(), level.getMinY());
        int maxY = Math.min(selection.max().y(), level.getMaxY());
        if (minY > maxY) {
            return new ExportPlan(selection, List.of(), List.of());
        }

        int minChunkX = Math.floorDiv(selection.min().x(), 16);
        int maxChunkX = Math.floorDiv(selection.max().x(), 16);
        int minChunkZ = Math.floorDiv(selection.min().z(), 16);
        int maxChunkZ = Math.floorDiv(selection.max().z(), 16);
        int minSectionY = Math.floorDiv(minY, 16);
        int maxSectionY = Math.floorDiv(maxY, 16);
        List<ExportPlan.SectionWork> sections = new ArrayList<>();
        List<ExportPlan.MissingChunk> missing = new ArrayList<>();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    missing.add(new ExportPlan.MissingChunk(chunkX, chunkZ));
                    continue;
                }
                planSections(selection, minY, maxY,
                        minSectionY, maxSectionY, chunkX, chunkZ, sections);
            }
        }
        return new ExportPlan(selection, sections, missing);
    }

    public ExportPlan planBatch(
            ClientLevel level, Selection selection, List<ChunkCoordinate> chunks) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(chunks, "chunks");
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Rolling batch requires at least one chunk");
        }
        String dimension = level.dimension().identifier().toString();
        if (!dimension.equals(selection.min().dimension())) {
            throw new IllegalArgumentException("Selection dimension does not match the active client level");
        }
        int minY = Math.max(selection.min().y(), level.getMinY());
        int maxY = Math.min(selection.max().y(), level.getMaxY());
        if (minY > maxY) {
            return new ExportPlan(selection, List.of(), List.of());
        }
        Set<Long> authorized = new HashSet<>();
        for (ChunkCoordinate chunk : chunks) {
            authorized.add(ChunkPos.pack(chunk.x(), chunk.z()));
        }
        int minSectionY = Math.floorDiv(minY, 16);
        int maxSectionY = Math.floorDiv(maxY, 16);
        List<ExportPlan.SectionWork> sections = new ArrayList<>();
        for (ChunkCoordinate chunk : chunks) {
            if (!authorized.contains(ChunkPos.pack(chunk.x(), chunk.z()))) {
                throw new IllegalArgumentException("Batch chunk is outside the authorized window");
            }
            if (!level.hasChunk(chunk.x(), chunk.z())) {
                throw new IllegalStateException("Batch chunk is not readable on the client: "
                        + chunk.x() + "," + chunk.z());
            }
            planSections(selection, minY, maxY,
                    minSectionY, maxSectionY, chunk.x(), chunk.z(), sections);
        }
        return new ExportPlan(selection, sections, List.of());
    }

    private static void planSections(
            Selection selection,
            int minY,
            int maxY,
            int minSectionY,
            int maxSectionY,
            int chunkX,
            int chunkZ,
            List<ExportPlan.SectionWork> sections) {
        for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
            int sectionMinX = Math.max(selection.min().x(), chunkX * 16);
            int sectionMaxX = Math.min(selection.max().x(), chunkX * 16 + 15);
            int sectionMinY = Math.max(minY, sectionY * 16);
            int sectionMaxY = Math.min(maxY, sectionY * 16 + 15);
            int sectionMinZ = Math.max(selection.min().z(), chunkZ * 16);
            int sectionMaxZ = Math.min(selection.max().z(), chunkZ * 16 + 15);
            sections.add(new ExportPlan.SectionWork(
                    new ChunkSectionRef(chunkX, sectionY, chunkZ),
                    sectionMinX, sectionMinY, sectionMinZ,
                    sectionMaxX, sectionMaxY, sectionMaxZ));
        }
    }
}
