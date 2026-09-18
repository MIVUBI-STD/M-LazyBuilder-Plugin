package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSetBuilder;
import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionTypes;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Chunk-local position mask for blocks owned by authoritative BLOCK_ENTITY batches. */
public final class AxiomBlockEntityPositionMask {
    private final Map<Long, Set<Long>> positionsByChunk;

    private AxiomBlockEntityPositionMask(Map<Long, Set<Long>> positionsByChunk) {
        this.positionsByChunk = Map.copyOf(positionsByChunk);
    }

    public static AxiomBlockEntityPositionMask from(StoredChangeSet stored) throws IOException {
        Map<Long, Set<Long>> mutable = new HashMap<>();
        stored.visitExtensions(frame -> {
            if (HistoryExtensionTypes.BLOCK_ENTITY.equals(frame.typeId())) {
                mutable.computeIfAbsent(
                        chunkKey(frame.chunkX(), frame.chunkZ()),
                        ignored -> new HashSet<>()
                ).add(frame.localKey());
            }
            return true;
        });
        Map<Long, Set<Long>> frozen = new HashMap<>();
        mutable.forEach((key, value) -> frozen.put(key, Set.copyOf(value)));
        return new AxiomBlockEntityPositionMask(frozen);
    }

    public boolean contains(int chunkX, int chunkZ, long localKey) {
        Set<Long> positions = positionsByChunk.get(chunkKey(chunkX, chunkZ));
        return positions != null && positions.contains(localKey);
    }

    public boolean empty() {
        return positionsByChunk.isEmpty();
    }

    public ChunkChangeSet withoutOwnedPositions(ChunkChangeSet chunk) {
        Set<Long> positions = positionsByChunk.get(chunkKey(chunk.chunkX(), chunk.chunkZ()));
        if (positions == null || positions.isEmpty()) return chunk;

        ChunkChangeSetBuilder builder =
                new ChunkChangeSetBuilder(chunk.chunkX(), chunk.chunkZ());
        long[] packed = chunk.positions();
        for (int i = 0; i < packed.length; i++) {
            if (positions.contains(packed[i])) continue;
            int x = Math.addExact(
                    Math.multiplyExact(chunk.chunkX(), 16),
                    com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition.localX(packed[i]));
            int z = Math.addExact(
                    Math.multiplyExact(chunk.chunkZ(), 16),
                    com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition.localZ(packed[i]));
            int y = com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition.y(packed[i]);
            builder.addWorld(x, y, z, chunk.beforeState(i), chunk.afterState(i));
        }
        return builder.build();
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }
}
