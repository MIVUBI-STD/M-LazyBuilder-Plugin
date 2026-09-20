package com.halokaryamedia.lazybuilder.performance;

/** Persisted Performance Manager policy preferences. */
public record PerformancePreferences(
        boolean backgroundFpsPolicy,
        int unfocusedFpsLimit,
        int minimizedFpsLimit,
        boolean entityCulling,
        boolean blockEntityCulling,
        boolean renderingOptimizations,
        boolean memoryOptimizations
) {
    public static PerformancePreferences defaults() {
        return new PerformancePreferences(true, 30, 10, false, false, true, true);
    }

    public PerformancePreferences {
        unfocusedFpsLimit = sanitizeLimit(unfocusedFpsLimit, 30);
        minimizedFpsLimit = sanitizeLimit(minimizedFpsLimit, 10);
    }

    public PerformancePreferences withBackgroundFpsPolicy(boolean enabled) {
        return copy(enabled, unfocusedFpsLimit, minimizedFpsLimit, entityCulling, blockEntityCulling,
                renderingOptimizations, memoryOptimizations);
    }

    public PerformancePreferences withUnfocusedFpsLimit(int fps) {
        return copy(backgroundFpsPolicy, fps, minimizedFpsLimit, entityCulling, blockEntityCulling,
                renderingOptimizations, memoryOptimizations);
    }

    public PerformancePreferences withMinimizedFpsLimit(int fps) {
        return copy(backgroundFpsPolicy, unfocusedFpsLimit, fps, entityCulling, blockEntityCulling,
                renderingOptimizations, memoryOptimizations);
    }

    /** User-facing "Skip Hidden Objects": one understandable choice owns both internal culling paths. */
    public PerformancePreferences withHiddenObjectSkipping(boolean enabled) {
        return copy(backgroundFpsPolicy, unfocusedFpsLimit, minimizedFpsLimit, enabled, enabled,
                renderingOptimizations, memoryOptimizations);
    }

    public PerformancePreferences withRenderingOptimizations(boolean enabled) {
        return copy(backgroundFpsPolicy, unfocusedFpsLimit, minimizedFpsLimit, entityCulling,
                blockEntityCulling, enabled, memoryOptimizations);
    }

    public PerformancePreferences withMemoryOptimizations(boolean enabled) {
        return copy(backgroundFpsPolicy, unfocusedFpsLimit, minimizedFpsLimit, entityCulling,
                blockEntityCulling, renderingOptimizations, enabled);
    }

    public boolean hiddenObjectSkipping() {
        return entityCulling && blockEntityCulling;
    }

    private static PerformancePreferences copy(
            boolean backgroundFpsPolicy,
            int unfocusedFpsLimit,
            int minimizedFpsLimit,
            boolean entityCulling,
            boolean blockEntityCulling,
            boolean renderingOptimizations,
            boolean memoryOptimizations
    ) {
        return new PerformancePreferences(
                backgroundFpsPolicy,
                unfocusedFpsLimit,
                minimizedFpsLimit,
                entityCulling,
                blockEntityCulling,
                renderingOptimizations,
                memoryOptimizations
        );
    }

    private static int sanitizeLimit(int value, int fallback) {
        return value >= 5 && value <= 260 ? value : fallback;
    }
}
