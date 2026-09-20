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

    private static int sanitizeLimit(int value, int fallback) {
        return value >= 5 && value <= 260 ? value : fallback;
    }
}
