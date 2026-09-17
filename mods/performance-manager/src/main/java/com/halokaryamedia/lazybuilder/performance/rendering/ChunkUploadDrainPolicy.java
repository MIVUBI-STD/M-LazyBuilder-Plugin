package com.halokaryamedia.lazybuilder.performance.rendering;

/** Pure policy for keeping render-thread chunk uploads from monopolizing one frame. */
public final class ChunkUploadDrainPolicy {
    private static final int TASK_BUDGET_PER_PASS = 48;

    private ChunkUploadDrainPolicy() {
    }

    public static int taskBudget(boolean stopping) {
        return stopping ? Integer.MAX_VALUE : TASK_BUDGET_PER_PASS;
    }

    public static boolean shouldContinue(int processedTasks, boolean stopping) {
        return stopping || processedTasks < TASK_BUDGET_PER_PASS;
    }
}
