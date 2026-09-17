package com.halokaryamedia.lazybuilder.builder.region;

import java.util.Objects;

/**
 * Candidate world-space bounds for one chunk-local slice of an operation.
 */
public record ChunkWorkUnit(int chunkX, int chunkZ, BlockBounds candidateBounds) {
    public ChunkWorkUnit {
        Objects.requireNonNull(candidateBounds, "candidateBounds");
        if (candidateBounds.minChunkX() != chunkX || candidateBounds.maxChunkX() != chunkX
                || candidateBounds.minChunkZ() != chunkZ || candidateBounds.maxChunkZ() != chunkZ) {
            throw new IllegalArgumentException("candidateBounds must remain inside the declared chunk");
        }
    }

    public long candidateBlockCount() {
        return candidateBounds.blockCount();
    }
}
