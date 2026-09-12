package com.halokaryamedia.lazybuilder.world.application;

/** Inclusive block-space rectangle selected by the client map UI. */
public record WorldAreaSelection(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
    public WorldAreaSelection {
        if (minBlockX > maxBlockX) throw new IllegalArgumentException("minBlockX must not exceed maxBlockX");
        if (minBlockZ > maxBlockZ) throw new IllegalArgumentException("minBlockZ must not exceed maxBlockZ");
    }

    public static WorldAreaSelection ofCorners(int x1, int z1, int x2, int z2) {
        return new WorldAreaSelection(Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2));
    }

    public int minChunkX() { return Math.floorDiv(minBlockX, 16); }
    public int minChunkZ() { return Math.floorDiv(minBlockZ, 16); }
    public int maxChunkX() { return Math.floorDiv(maxBlockX, 16); }
    public int maxChunkZ() { return Math.floorDiv(maxBlockZ, 16); }

    public long chunkCount() {
        long width = (long) maxChunkX() - minChunkX() + 1L;
        long depth = (long) maxChunkZ() - minChunkZ() + 1L;
        return Math.multiplyExact(width, depth);
    }
}
