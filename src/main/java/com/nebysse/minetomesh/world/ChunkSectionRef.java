package com.nebysse.minetomesh.world;

public record ChunkSectionRef(int chunkX, int sectionY, int chunkZ)
        implements Comparable<ChunkSectionRef> {
    @Override
    public int compareTo(ChunkSectionRef other) {
        int x = Integer.compare(chunkX, other.chunkX);
        if (x != 0) {
            return x;
        }
        int z = Integer.compare(chunkZ, other.chunkZ);
        if (z != 0) {
            return z;
        }
        return Integer.compare(sectionY, other.sectionY);
    }
}
