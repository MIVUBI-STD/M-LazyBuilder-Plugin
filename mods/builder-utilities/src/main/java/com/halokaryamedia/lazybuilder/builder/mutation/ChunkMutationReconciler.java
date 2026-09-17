package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;

import java.util.Objects;

/**
 * Classifies actual world state against the before/after states recorded for one
 * chunk mutation. Recovery code can use this classification without trusting a
 * separate "chunk applied" marker that may be stale after a crash.
 */
public final class ChunkMutationReconciler {
    private ChunkMutationReconciler() {
    }

    public static ChunkReconciliationReport reconcile(ChunkChangeSet changes, WorldBlockStateSource world) {
        Objects.requireNonNull(changes, "changes");
        Objects.requireNonNull(world, "world");

        long before = 0;
        long after = 0;
        long conflicts = 0;
        long[] positions = changes.positions();

        int chunkBaseX = Math.multiplyExact(changes.chunkX(), 16);
        int chunkBaseZ = Math.multiplyExact(changes.chunkZ(), 16);
        for (int i = 0; i < positions.length; i++) {
            long packed = positions[i];
            int worldX = Math.addExact(chunkBaseX, LocalBlockPosition.localX(packed));
            int worldZ = Math.addExact(chunkBaseZ, LocalBlockPosition.localZ(packed));
            String actual = world.readBlockState(worldX, LocalBlockPosition.y(packed), worldZ);
            if (actual == null || actual.isBlank()) {
                throw new IllegalStateException("world returned a blank block state");
            }

            String beforeState = changes.beforeState(i);
            String afterState = changes.afterState(i);
            if (actual.equals(afterState)) {
                after++;
            } else if (actual.equals(beforeState)) {
                before++;
            } else {
                conflicts++;
            }
        }

        return new ChunkReconciliationReport(
                changes.chunkX(),
                changes.chunkZ(),
                positions.length,
                before,
                after,
                conflicts,
                classify(positions.length, before, after, conflicts)
        );
    }

    private static ReconciliationState classify(long total, long before, long after, long conflicts) {
        if (total == 0) return ReconciliationState.EMPTY;
        if (conflicts > 0) return ReconciliationState.CONFLICT;
        if (before == total) return ReconciliationState.NOT_APPLIED;
        if (after == total) return ReconciliationState.FULLY_APPLIED;
        return ReconciliationState.PARTIALLY_APPLIED;
    }
}
