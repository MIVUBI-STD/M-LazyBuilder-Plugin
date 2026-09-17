package com.halokaryamedia.lazybuilder.performance.rendering;

/** Pure policy for dropping redundant vanilla chunk rebuild requests. */
public final class ChunkRebuildPolicy {
    private ChunkRebuildPolicy() {
    }

    /**
     * Returns true only when the incoming request cannot strengthen the rebuild state.
     * A normal pending rebuild absorbs another normal request; an important pending rebuild
     * absorbs either kind; an incoming important request upgrades a normal pending rebuild.
     */
    public static boolean shouldSkip(
            boolean rebuildAlreadyScheduled,
            boolean importantRebuildAlreadyScheduled,
            boolean incomingImportant
    ) {
        if (!rebuildAlreadyScheduled) return false;
        if (importantRebuildAlreadyScheduled) return true;
        return !incomingImportant;
    }
}
