package com.halokaryamedia.lazybuilder.performance;

/**
 * Allocation-free rolling frame monitor used to detect sustained client pressure.
 * Thresholds are intentionally internal until runtime profiling proves a user-facing knob is needed.
 */
public final class FrameMonitor {
    private static final int WINDOW_SIZE = 60;
    private static final double ELEVATED_AVERAGE_MS = 25.0D;
    private static final double HEAVY_AVERAGE_MS = 40.0D;
    private static final double BAD_FRAME_MS = 50.0D;
    private static final double SEVERE_FRAME_MS = 75.0D;

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
    private long previousFrameNanos = Long.MIN_VALUE;

    public void recordFrame(long nowNanos) {
        if (previousFrameNanos == Long.MIN_VALUE) {
            previousFrameNanos = nowNanos;
            return;
        }

        long deltaNanos = nowNanos - previousFrameNanos;
        previousFrameNanos = nowNanos;
        if (deltaNanos <= 0L) return;

        recordFrameTimeMs(deltaNanos / 1_000_000.0D);
    }

    /**
     * Stops wall-clock gaps from background/minimized rendering being interpreted as lag.
     * The next focused frame becomes a fresh timing baseline.
     */
    public void pauseFrameClock() {
        previousFrameNanos = Long.MIN_VALUE;
    }

    void recordFrameTimeMs(double frameTimeMs) {
        if (!Double.isFinite(frameTimeMs) || frameTimeMs <= 0.0D) return;

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

        updatePressure(frameTimeMs);
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

    public int sampleCount() {
        return sampleCount;
    }

    private void updatePressure(double currentFrameMs) {
        double averageMs = averageFrameTimeMs();
        boolean severe = currentFrameMs >= SEVERE_FRAME_MS || averageMs >= HEAVY_AVERAGE_MS;
        boolean bad = severe || currentFrameMs >= BAD_FRAME_MS || averageMs >= ELEVATED_AVERAGE_MS;
        boolean stable = currentFrameMs < BAD_FRAME_MS && averageMs < ELEVATED_AVERAGE_MS;

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
}
