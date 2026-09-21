package com.halokaryamedia.lazybuilder.performance.rendering;

/**
 * Tiny bounded profitability window for optional optimizations.
 *
 * It never disables a correctness path permanently. Low-value work enters a finite cooldown,
 * then is sampled again so workload changes can recover automatically.
 */
public final class OptimizationProfitabilityWindow {
    private final int windowSize;
    private final int cooldownSize;
    private final double minimumUsefulRatio;
    private final long minimumSavings;

    private int samples;
    private int usefulSamples;
    private long savings;
    private int cooldownRemaining;

    public OptimizationProfitabilityWindow(
            int windowSize,
            int cooldownSize,
            double minimumUsefulRatio,
            long minimumSavings
    ) {
        if (windowSize <= 0 || cooldownSize < 0) throw new IllegalArgumentException("invalid window");
        this.windowSize = windowSize;
        this.cooldownSize = cooldownSize;
        this.minimumUsefulRatio = Math.max(0.0D, Math.min(1.0D, minimumUsefulRatio));
        this.minimumSavings = Math.max(0L, minimumSavings);
    }

    public boolean allowAttempt() {
        if (cooldownRemaining <= 0) return true;
        cooldownRemaining--;
        return false;
    }

    public void record(boolean useful, long savedUnits) {
        samples++;
        if (useful) usefulSamples++;
        savings += Math.max(0L, savedUnits);

        if (samples < windowSize) return;

        double ratio = usefulSamples / (double) samples;
        if (ratio < minimumUsefulRatio || savings < minimumSavings) {
            cooldownRemaining = cooldownSize;
        }
        resetWindow();
    }

    public Snapshot snapshot() {
        return new Snapshot(samples, usefulSamples, savings, cooldownRemaining);
    }

    public void reset() {
        resetWindow();
        cooldownRemaining = 0;
    }

    private void resetWindow() {
        samples = 0;
        usefulSamples = 0;
        savings = 0L;
    }

    public record Snapshot(int samples, int usefulSamples, long savings, int cooldownRemaining) {
        public double usefulRatio() {
            return samples <= 0 ? 0.0D : usefulSamples / (double) samples;
        }

        public boolean coolingDown() {
            return cooldownRemaining > 0;
        }
    }
}
