package com.halokaryamedia.lazybuilder.performance.rendering;

import java.util.List;

/**
 * Builds a draw-order-preserving plan over live terrain arena allocation handles.
 *
 * Only consecutive commands in the same logical region/layer arena may share one future arena
 * binding. Missing, empty, undersized, or wrong-layer handles remain vanilla fallback commands.
 */
public final class TerrainArenaDrawPlanner {
    private TerrainArenaDrawPlanner() {
    }

    public static Plan plan(List<TerrainRegionAllocationRegistry.Handle> handles, int expectedLayerSlot) {
        if (handles == null || handles.isEmpty()) return Plan.EMPTY;

        int eligible = 0;
        int fallback = 0;
        int batches = 0;
        long bindReductions = 0L;
        TerrainRegionAllocationRegistry.ArenaKey previousArena = null;
        int currentBatchSize = 0;

        for (TerrainRegionAllocationRegistry.Handle handle : handles) {
            if (!eligible(handle, expectedLayerSlot)) {
                fallback++;
                if (currentBatchSize > 1) bindReductions += currentBatchSize - 1L;
                currentBatchSize = 0;
                previousArena = null;
                continue;
            }

            eligible++;
            TerrainRegionAllocationRegistry.ArenaKey arena = handle.arenaKey();
            if (!arena.equals(previousArena)) {
                if (currentBatchSize > 1) bindReductions += currentBatchSize - 1L;
                batches++;
                currentBatchSize = 1;
                previousArena = arena;
            } else {
                currentBatchSize++;
            }
        }

        if (currentBatchSize > 1) bindReductions += currentBatchSize - 1L;
        return new Plan(eligible, fallback, batches, bindReductions);
    }

    public static Plan combine(Plan left, Plan right) {
        if (left == null) left = Plan.EMPTY;
        if (right == null) right = Plan.EMPTY;
        return new Plan(
                left.eligibleCommands + right.eligibleCommands,
                left.fallbackCommands + right.fallbackCommands,
                left.arenaBatches + right.arenaBatches,
                left.potentialBindReductions + right.potentialBindReductions
        );
    }

    private static boolean eligible(TerrainRegionAllocationRegistry.Handle handle, int expectedLayerSlot) {
        if (handle == null || handle.arenaKey() == null) return false;
        if (handle.arenaKey().layerSlot() != expectedLayerSlot) return false;
        if (handle.payloadBytes() <= 0L || handle.sizeBytes() <= 0L) return false;
        return handle.sizeBytes() >= TerrainRegionArenaPolicy.alignedSize(handle.payloadBytes());
    }

    public record Plan(
            int eligibleCommands,
            int fallbackCommands,
            int arenaBatches,
            long potentialBindReductions
    ) {
        public static final Plan EMPTY = new Plan(0, 0, 0, 0L);
    }
}
