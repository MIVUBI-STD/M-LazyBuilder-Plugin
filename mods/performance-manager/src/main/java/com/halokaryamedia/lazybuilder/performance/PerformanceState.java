package com.halokaryamedia.lazybuilder.performance;

/**
 * Passive snapshot of the current client performance state.
 *
 * This record contains observations only. It must never imply that Performance
 * Manager owns or mutates renderer, shader, culling, or graphics settings.
 */
public record PerformanceState(
        int fps,
        double approximateFrameTimeMs,
        long usedMemoryBytes,
        long maxMemoryBytes,
        int renderDistance,
        int simulationDistance,
        boolean windowFocused,
        boolean windowMinimized,
        PerformanceCapabilities capabilities
) {
    public double usedMemoryRatio() {
        if (maxMemoryBytes <= 0L) return 0.0D;
        return Math.min(1.0D, (double) usedMemoryBytes / (double) maxMemoryBytes);
    }
}
