package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;

import java.util.Objects;

public final class ChunkChangeSetTransforms {
    private ChunkChangeSetTransforms() {
    }

    /** Swaps BEFORE/AFTER states while preserving chunk-local positions and palette. */
    public static ChunkChangeSet reverse(ChunkChangeSet chunk) {
        Objects.requireNonNull(chunk, "chunk");
        return new ChunkChangeSet(
                chunk.chunkX(),
                chunk.chunkZ(),
                chunk.palette(),
                chunk.positions(),
                chunk.afterStates(),
                chunk.beforeStates()
        );
    }
}
