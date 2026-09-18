package com.halokaryamedia.lazybuilder.world.application;

import java.util.Set;

/**
 * Canonical inclusive export rectangle for one vanilla dimension.
 *
 * <p>Area export is chunk-owned: client map selection may present block coordinates,
 * but the server always expands the request to complete 16x16 chunks before any
 * pruning/conversion work. A map selection is dimension-local; applying the same
 * coordinates to another dimension would describe a different place.</p>
 */
public record WorldAreaSelection(String dimensionId, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
    private static final int CHUNK_BLOCKS = 16;
    /** Safety/resource ceiling: at most 512 x 512 chunks per area export request. */
    public static final long MAX_CHUNK_COUNT = 512L * 512L;
    private static final Set<String> VANILLA_DIMENSIONS = Set.of(
            "minecraft:overworld",
            "minecraft:the_nether",
            "minecraft:the_end"
    );

    public WorldAreaSelection {
        if (dimensionId == null || dimensionId.isBlank()) {
            throw new IllegalArgumentException("dimensionId must not be blank");
        }
        dimensionId = dimensionId.strip();
        if (!VANILLA_DIMENSIONS.contains(dimensionId)) {
            throw new IllegalArgumentException("Selected Area export does not support dimension: " + dimensionId);
        }
        if (minBlockX > maxBlockX) throw new IllegalArgumentException("minBlockX must not exceed maxBlockX");
        if (minBlockZ > maxBlockZ) throw new IllegalArgumentException("minBlockZ must not exceed maxBlockZ");

        int minChunkX = Math.floorDiv(minBlockX, CHUNK_BLOCKS);
        int minChunkZ = Math.floorDiv(minBlockZ, CHUNK_BLOCKS);
        int maxChunkX = Math.floorDiv(maxBlockX, CHUNK_BLOCKS);
        int maxChunkZ = Math.floorDiv(maxBlockZ, CHUNK_BLOCKS);
        long chunkWidth = (long) maxChunkX - minChunkX + 1L;
        long chunkDepth = (long) maxChunkZ - minChunkZ + 1L;
        long chunks;
        try {
            chunks = Math.multiplyExact(chunkWidth, chunkDepth);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Selected Area is too large", overflow);
        }
        if (chunks > MAX_CHUNK_COUNT) {
            throw new IllegalArgumentException("Selected Area exceeds the maximum of " + MAX_CHUNK_COUNT + " chunks");
        }

        minBlockX = Math.multiplyExact(minChunkX, CHUNK_BLOCKS);
        minBlockZ = Math.multiplyExact(minChunkZ, CHUNK_BLOCKS);
        maxBlockX = Math.addExact(Math.multiplyExact(maxChunkX, CHUNK_BLOCKS), CHUNK_BLOCKS - 1);
        maxBlockZ = Math.addExact(Math.multiplyExact(maxChunkZ, CHUNK_BLOCKS), CHUNK_BLOCKS - 1);
    }

    public static WorldAreaSelection ofCorners(String dimensionId, int x1, int z1, int x2, int z2) {
        return new WorldAreaSelection(
                dimensionId,
                Math.min(x1, x2),
                Math.min(z1, z2),
                Math.max(x1, x2),
                Math.max(z1, z2)
        );
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
