package com.halokaryamedia.lazybuilder.world.application;

/**
 * Canonical inclusive export rectangle.
 *
 * <p>Area export is chunk-owned: client map selection may present block coordinates,
 * but the server always expands the request to complete 16x16 chunks before any
 * pruning/conversion work. This keeps UI, protocol and converter behavior aligned
 * and prevents a custom client from implying unsupported partial-chunk exports.</p>
 */
public record WorldAreaSelection(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
    private static final int CHUNK_BLOCKS = 16;

    public WorldAreaSelection {
        if (minBlockX > maxBlockX) throw new IllegalArgumentException("minBlockX must not exceed maxBlockX");
        if (minBlockZ > maxBlockZ) throw new IllegalArgumentException("minBlockZ must not exceed maxBlockZ");

        int minChunkX = Math.floorDiv(minBlockX, CHUNK_BLOCKS);
        int minChunkZ = Math.floorDiv(minBlockZ, CHUNK_BLOCKS);
        int maxChunkX = Math.floorDiv(maxBlockX, CHUNK_BLOCKS);
        int maxChunkZ = Math.floorDiv(maxBlockZ, CHUNK_BLOCKS);

        minBlockX = Math.multiplyExact(minChunkX, CHUNK_BLOCKS);
        minBlockZ = Math.multiplyExact(minChunkZ, CHUNK_BLOCKS);
        maxBlockX = Math.addExact(Math.multiplyExact(maxChunkX, CHUNK_BLOCKS), CHUNK_BLOCKS - 1);
        maxBlockZ = Math.addExact(Math.multiplyExact(maxChunkZ, CHUNK_BLOCKS), CHUNK_BLOCKS - 1);
    }

    public static WorldAreaSelection ofCorners(int x1, int z1, int x2, int z2) {
        return new WorldAreaSelection(Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2));
    }

    public int minChunkX() { return Math.floorDiv(minBlockX, CHUNK_BLOCKS); }
    public int minChunkZ() { return Math.floorDiv(minBlockZ, CHUNK_BLOCKS); }
    public int maxChunkX() { return Math.floorDiv(maxBlockX, CHUNK_BLOCKS); }
    public int maxChunkZ() { return Math.floorDiv(maxBlockZ, CHUNK_BLOCKS); }

    public int chunkWidth() { return maxChunkX() - minChunkX() + 1; }
    public int chunkDepth() { return maxChunkZ() - minChunkZ() + 1; }
    public int blockWidth() { return Math.multiplyExact(chunkWidth(), CHUNK_BLOCKS); }
    public int blockDepth() { return Math.multiplyExact(chunkDepth(), CHUNK_BLOCKS); }

    public long chunkCount() {
        return Math.multiplyExact((long) chunkWidth(), (long) chunkDepth());
    }
}
