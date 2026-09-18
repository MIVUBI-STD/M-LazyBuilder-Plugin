package com.halokaryamedia.lazybuilder.performance.rendering;

import com.halokaryamedia.lazybuilder.performance.FramePressure;

/** Pure policy for keeping render-thread chunk uploads from monopolizing one frame. */
public final class ChunkUploadDrainPolicy {
    private static final int NORMAL_BUDGET = 48;
    private static final int ELEVATED_BUDGET = 24;
    private static final int HEAVY_BUDGET = 8;

    private ChunkUploadDrainPolicy() {
    }

    public static int taskBudget(boolean stopping, FramePressure pressure) {
        if (stopping) return Integer.MAX_VALUE;
        if (pressure == FramePressure.HEAVY) return HEAVY_BUDGET;
        if (pressure == FramePressure.ELEVATED) return ELEVATED_BUDGET;
        return NORMAL_BUDGET;
    }

    public static boolean shouldContinue(int processedTasks, boolean stopping, FramePressure pressure) {
        return stopping || processedTasks < taskBudget(false, pressure);
    }
}
