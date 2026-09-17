package com.halokaryamedia.lazybuilder.performance.rendering;

import java.util.concurrent.atomic.LongAdder;

/** Low-overhead counters for first-party chunk-pipeline decisions and pressure signals. */
public final class ChunkPipelineMetrics {
    private static final LongAdder COALESCED_REBUILD_REQUESTS = new LongAdder();
    private static final LongAdder BUFFER_ACQUIRE_MISSES = new LongAdder();

    private ChunkPipelineMetrics() {
    }

    public static void recordCoalescedRebuild() {
        COALESCED_REBUILD_REQUESTS.increment();
    }

    public static long coalescedRebuildRequests() {
        return COALESCED_REBUILD_REQUESTS.sum();
    }

    public static void recordBufferAcquireMiss() {
        BUFFER_ACQUIRE_MISSES.increment();
    }

    public static long bufferAcquireMisses() {
        return BUFFER_ACQUIRE_MISSES.sum();
    }

    static void resetForTest() {
        COALESCED_REBUILD_REQUESTS.reset();
        BUFFER_ACQUIRE_MISSES.reset();
    }
}
