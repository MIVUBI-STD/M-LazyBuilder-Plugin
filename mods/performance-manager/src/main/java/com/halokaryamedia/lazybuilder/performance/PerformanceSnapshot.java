package com.halokaryamedia.lazybuilder.performance;

/** On-demand diagnostic snapshot of current LazyBuilder client performance. */
public record PerformanceSnapshot(
        int fps,
        double currentFrameTimeMs,
        double averageFrameTimeMs,
        double worstRecentFrameTimeMs,
        long usedMemoryBytes,
        long maxMemoryBytes,
        int renderDistance,
        int simulationDistance,
        boolean windowFocused,
        boolean windowMinimized,
        FramePressure pressure
) {
    public double usedMemoryRatio() {
        if (maxMemoryBytes <= 0L) return 0.0D;
        return Math.min(1.0D, (double) usedMemoryBytes / (double) maxMemoryBytes);
    }
}
