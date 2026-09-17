package com.halokaryamedia.lazybuilder.performance.rendering;

import java.util.concurrent.atomic.LongAdder;

/** Low-overhead counters for first-party chunk-pipeline decisions. */
public final class ChunkPipelineMetrics {
    private static final LongAdder COALESCED_REBUILD_REQUESTS = new LongAdder();

    private ChunkPipelineMetrics() {
    }

    public static void recordCoalescedRebuild() {
        COALESCED_REBUILD_REQUESTS.increment();
    }

    public static long coalescedRebuildRequests() {
        return COALESCED_REBUILD_REQUESTS.sum();
    }

    static void resetForTest() {
        COALESCED_REBUILD_REQUESTS.reset();
    }
}
