package com.halokaryamedia.lazybuilder.client;

import java.util.UUID;

/**
 * Chunk-aligned export selection state.
 *
 * <p>The screen remains responsible for interaction and rendering. This class
 * owns only selection identity and bounds so map lifecycle code no longer
 * stores those values as unrelated top-level fields.</p>
 */
final class MapAreaSelectionState {
    boolean active;
    UUID worldId;
    int minChunkX;
    int maxChunkX;
    int minChunkZ;
    int maxChunkZ;

    boolean ownsWorld(UUID candidateWorldId) {
        return worldId != null && worldId.equals(candidateWorldId);
    }

    void activate(UUID worldId, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
        this.worldId = worldId;
        this.minChunkX = Math.min(minChunkX, maxChunkX);
        this.maxChunkX = Math.max(minChunkX, maxChunkX);
        this.minChunkZ = Math.min(minChunkZ, maxChunkZ);
        this.maxChunkZ = Math.max(minChunkZ, maxChunkZ);
        this.active = true;
    }

    void clear() {
        active = false;
        worldId = null;
    }

    int minBlockX(int chunkBlocks) {
        return minChunkX * chunkBlocks;
    }

    int maxBlockX(int chunkBlocks) {
        return maxChunkX * chunkBlocks + chunkBlocks - 1;
    }

    int minBlockZ(int chunkBlocks) {
        return minChunkZ * chunkBlocks;
    }

    int maxBlockZ(int chunkBlocks) {
        return maxChunkZ * chunkBlocks + chunkBlocks - 1;
    }
}
