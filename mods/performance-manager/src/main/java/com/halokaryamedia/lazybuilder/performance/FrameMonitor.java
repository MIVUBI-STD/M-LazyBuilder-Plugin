package com.halokaryamedia.lazybuilder.performance;

/**
 * Allocation-free rolling frame monitor used to detect sustained client pressure.
 * Thresholds are derived from the user's effective foreground FPS target with
 * conservative absolute floors so intentionally lower FPS targets are not
 * misclassified as lag.
 */
public final class FrameMonitor {
    private static final int WINDOW_SIZE = 60;
    private static final double DEFAULT_TARGET_FRAME_MS = 1000.0D / 60.0D;
    private static final double ELEVATED_MULTIPLIER = 1.35D;
    private static final double HEAVY_MULTIPLIER = 2.0D;
    private static final double BAD_FRAME_MULTIPLIER = 2.0D;
    private static final double SEVERE_FRAME_MULTIPLIER = 3.0D;
    private static final double ELEVATED_FLOOR_MS = 25.0D;
    private static final double HEAVY_FLOOR_MS = 40.0D;
    private static final double BAD_FRAME_FLOOR_MS = 50.0D;
    private static final double SEVERE_FRAME_FLOOR_MS = 75.0D;
    private static final double DISCONTINUITY_FLOOR_MS = 250.0D;
    private static final double DISCONTINUITY_MULTIPLIER = 8.0D;

    private final double[] frameTimes = new double[WINDOW_SIZE];
    private int sampleCount;
    private int cursor;
    private double rollingTotalMs;
    private double currentFrameMs;
    private double worstRecentMs;
    private int badSamples;
    private int severeSamples;
    private int stableFrames;
    private FramePressure pressure = FramePressure.NORMAL;
    private long currentFrameNanos;
    private long previousFrameNanos = Long.MIN_VALUE;

    public void recordFrame(long nowNanos, int targetFps) {
        currentFrameNanos = nowNanos;
        if (previousFrameNanos == Long.MIN_VALUE) {
            previousFrameNanos = nowNanos;
            return;
        }

        long deltaNanos = nowNanos - previousFrameNanos;
        previousFrameNanos = nowNanos;
        if (deltaNanos <= 0L) return;

        double targetFrameMs = targetFrameMs(targetFps);
        double frameTimeMs = deltaNanos / 1_000_000.0D;
        double discontinuityMs = Math.max(DISCONTINUITY_FLOOR_MS, targetFrameMs * DISCONTINUITY_MULTIPLIER);
        if (frameTimeMs > discontinuityMs) {
            pauseFrameClock();
            previousFrameNanos = nowNanos;
            return;
        }

        recordFrameTimeMs(frameTimeMs, targetFrameMs);
    }

    /** Compatibility helper for focused tests and internal callers using a 60 FPS baseline. */
    public void recordFrame(long nowNanos) {
        recordFrame(nowNanos, 60);
    }

    /**
     * Stops wall-clock gaps from background/minimized/non-world rendering being interpreted as lag.
     * The next focused world frame becomes a fresh timing baseline.
     */
    public void pauseFrameClock() {
        previousFrameNanos = Long.MIN_VALUE;
        currentFrameNanos = 0L;
    }

    /** Resets rolling pressure history when the active world/session changes. */
    public void resetSession() {
        java.util.Arrays.fill(frameTimes, 0.0D);
        sampleCount = 0;
        cursor = 0;
        rollingTotalMs = 0.0D;
        currentFrameMs = 0.0D;
        worstRecentMs = 0.0D;
        badSamples = 0;
        severeSamples = 0;
        stableFrames = 0;
        pressure = FramePressure.NORMAL;
        pauseFrameClock();
    }

    void recordFrameTimeMs(double frameTimeMs) {
        recordFrameTimeMs(frameTimeMs, DEFAULT_TARGET_FRAME_MS);
    }

    void recordFrameTimeMs(double frameTimeMs, double targetFrameMs) {
        if (!Double.isFinite(frameTimeMs) || frameTimeMs <= 0.0D) return;
        if (!Double.isFinite(targetFrameMs) || targetFrameMs <= 0.0D) targetFrameMs = DEFAULT_TARGET_FRAME_MS;

        currentFrameMs = frameTimeMs;
        double replaced = frameTimes[cursor];
        if (sampleCount < WINDOW_SIZE) {
            sampleCount++;
        } else {
            rollingTotalMs -= replaced;
        }

        frameTimes[cursor] = frameTimeMs;
        rollingTotalMs += frameTimeMs;
        cursor = (cursor + 1) % WINDOW_SIZE;

        if (frameTimeMs >= worstRecentMs) {
            worstRecentMs = frameTimeMs;
        } else if (replaced >= worstRecentMs) {
            recomputeWorst();
        }

        updatePressure(frameTimeMs, targetFrameMs);
    }

    public double currentFrameTimeMs() {
        return currentFrameMs;
    }

    public double averageFrameTimeMs() {
        return sampleCount == 0 ? 0.0D : rollingTotalMs / sampleCount;
    }

    public double worstRecentFrameTimeMs() {
        return worstRecentMs;
    }

    public FramePressure pressure() {
        return pressure;
    }

    public long currentFrameNanos() {
        return currentFrameNanos;
    }

    public int sampleCount() {
        return sampleCount;
    }

    /**
     * On-demand distribution snapshot. Sorting happens only when diagnostics/proof asks for it,
     * never on the per-frame hot path.
     */
    public TimingSnapshot timingSnapshot() {
        if (sampleCount == 0) return TimingSnapshot.EMPTY;

        double[] sorted = java.util.Arrays.copyOf(frameTimes, sampleCount);
        java.util.Arrays.sort(sorted);

        int over16 = 0;
        int over25 = 0;
        int over33 = 0;
        int over50 = 0;
        for (double value : sorted) {
            if (value > 16.67D) over16++;
            if (value > 25.0D) over25++;
            if (value > 33.33D) over33++;
            if (value > 50.0D) over50++;
        }

        return new TimingSnapshot(
                percentile(sorted, 0.50D),
                percentile(sorted, 0.90D),
                percentile(sorted, 0.95D),
                percentile(sorted, 0.99D),
                percentile(sorted, 0.999D),
                over16,
                over25,
                over33,
                over50,
                sampleCount
        );
    }

    private static double percentile(double[] sorted, double percentile) {
        if (sorted == null || sorted.length == 0) return 0.0D;
        double clamped = Math.max(0.0D, Math.min(1.0D, percentile));
        int index = (int) Math.ceil(clamped * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    public record TimingSnapshot(
            double p50Ms,
            double p90Ms,
            double p95Ms,
            double p99Ms,
            double p999Ms,
            int framesOver16_67Ms,
            int framesOver25Ms,
            int framesOver33_33Ms,
            int framesOver50Ms,
            int samples
    ) {
        private static final TimingSnapshot EMPTY =
                new TimingSnapshot(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0, 0, 0, 0, 0);
    }

    private void updatePressure(double currentFrameMs, double targetFrameMs) {
        double averageMs = averageFrameTimeMs();
        double elevatedAverageMs = Math.max(ELEVATED_FLOOR_MS, targetFrameMs * ELEVATED_MULTIPLIER);
        double heavyAverageMs = Math.max(HEAVY_FLOOR_MS, targetFrameMs * HEAVY_MULTIPLIER);
        double badFrameMs = Math.max(BAD_FRAME_FLOOR_MS, targetFrameMs * BAD_FRAME_MULTIPLIER);
        double severeFrameMs = Math.max(SEVERE_FRAME_FLOOR_MS, targetFrameMs * SEVERE_FRAME_MULTIPLIER);

        boolean severe = currentFrameMs >= severeFrameMs || averageMs >= heavyAverageMs;
        boolean bad = severe || currentFrameMs >= badFrameMs || averageMs >= elevatedAverageMs;
        boolean stable = currentFrameMs < badFrameMs && averageMs < elevatedAverageMs;

        severeSamples = severe ? severeSamples + 1 : 0;
        badSamples = bad ? badSamples + 1 : 0;
        stableFrames = stable ? stableFrames + 1 : 0;

        if (pressure != FramePressure.HEAVY && severeSamples >= 3) {
            pressure = FramePressure.HEAVY;
            stableFrames = 0;
            return;
        }

        if (pressure == FramePressure.NORMAL && badSamples >= 3) {
            pressure = FramePressure.ELEVATED;
            stableFrames = 0;
            return;
        }

        if (pressure == FramePressure.HEAVY && stableFrames >= 30) {
            pressure = FramePressure.ELEVATED;
            stableFrames = 0;
            return;
        }

        if (pressure == FramePressure.ELEVATED && stableFrames >= 60) {
            pressure = FramePressure.NORMAL;
            stableFrames = 0;
        }
    }

    private void recomputeWorst() {
        double worst = 0.0D;
        for (int index = 0; index < sampleCount; index++) {
            worst = Math.max(worst, frameTimes[index]);
        }
        worstRecentMs = worst;
    }

    private static double targetFrameMs(int targetFps) {
        return targetFps <= 0 ? DEFAULT_TARGET_FRAME_MS : 1000.0D / targetFps;
    }
}
