package com.halokaryamedia.lazybuilder.builder.region;

import java.util.Optional;

/**
 * Inclusive world-space block bounds.
 */
public record BlockBounds(
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {
    public static final int CHUNK_SIZE = 16;

    public BlockBounds {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("minimum coordinates must not exceed maximum coordinates");
        }
    }

    public long blockCount() {
        long width = (long) maxX - minX + 1L;
        long height = (long) maxY - minY + 1L;
        long depth = (long) maxZ - minZ + 1L;
        return Math.multiplyExact(Math.multiplyExact(width, height), depth);
    }

    public int minChunkX() {
        return Math.floorDiv(minX, CHUNK_SIZE);
    }

    public int maxChunkX() {
        return Math.floorDiv(maxX, CHUNK_SIZE);
    }

    public int minChunkZ() {
        return Math.floorDiv(minZ, CHUNK_SIZE);
    }

    public int maxChunkZ() {
        return Math.floorDiv(maxZ, CHUNK_SIZE);
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public Optional<BlockBounds> intersectionWithChunk(int chunkX, int chunkZ) {
        int chunkMinX = Math.multiplyExact(chunkX, CHUNK_SIZE);
        int chunkMinZ = Math.multiplyExact(chunkZ, CHUNK_SIZE);
        int chunkMaxX = Math.addExact(chunkMinX, CHUNK_SIZE - 1);
        int chunkMaxZ = Math.addExact(chunkMinZ, CHUNK_SIZE - 1);

        int clippedMinX = Math.max(minX, chunkMinX);
        int clippedMaxX = Math.min(maxX, chunkMaxX);
        int clippedMinZ = Math.max(minZ, chunkMinZ);
        int clippedMaxZ = Math.min(maxZ, chunkMaxZ);
        if (clippedMinX > clippedMaxX || clippedMinZ > clippedMaxZ) {
            return Optional.empty();
        }
        return Optional.of(new BlockBounds(
                clippedMinX, minY, clippedMinZ,
                clippedMaxX, maxY, clippedMaxZ
        ));
    }
}
