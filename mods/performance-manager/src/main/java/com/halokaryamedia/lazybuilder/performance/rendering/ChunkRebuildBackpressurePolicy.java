package com.halokaryamedia.lazybuilder.performance.rendering;

import com.halokaryamedia.lazybuilder.performance.FramePressure;

/** Pure policy for conservative chunk rebuild backpressure. */
public final class ChunkRebuildBackpressurePolicy {
    static final int MAX_DEFERRED_TASKS = 128;
    private static final int MIN_SCHEDULER_BACKLOG = 8;
    private static final int MAX_FREE_BUFFERS_FOR_DEFERRAL = 1;

    private ChunkRebuildBackpressurePolicy() {
    }

    public static boolean shouldDefer(
            FramePressure pressure,
            boolean prioritized,
            int scheduledTasks,
            int freeBuffers,
            int deferredTasks
    ) {
        return pressure == FramePressure.HEAVY
                && !prioritized
                && scheduledTasks >= MIN_SCHEDULER_BACKLOG
                && freeBuffers <= MAX_FREE_BUFFERS_FOR_DEFERRAL
                && deferredTasks < MAX_DEFERRED_TASKS;
    }

    public static int releaseBudget(FramePressure pressure) {
        if (pressure == FramePressure.HEAVY) return 1;
        if (pressure == FramePressure.ELEVATED) return 4;
        return 16;
    }
}
