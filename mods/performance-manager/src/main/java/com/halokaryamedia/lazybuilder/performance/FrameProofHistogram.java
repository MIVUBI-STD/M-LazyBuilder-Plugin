package com.halokaryamedia.lazybuilder.performance;

/**
 * Bounded long-run frame-time histogram for proof mode.
 *
 * Unlike the 60-frame control window, this retains only fixed bucket counts and therefore
 * represents long builder sessions without keeping a frame history or allocating per frame.
 */
final class FrameProofHistogram {
    private static final double[] UPPER_MS = {
            4.0D, 5.0D, 6.0D, 7.0D, 8.0D, 10.0D, 12.0D, 14.0D,
            16.67D, 20.0D, 25.0D, 33.33D, 40.0D, 50.0D, 66.67D,
            100.0D, 150.0D, 250.0D
    };

    private final long[] counts = new long[UPPER_MS.length + 1];
    private long samples;
    private double maxObservedMs;
    private long framesOver16_67Ms;
    private long framesOver25Ms;
    private long framesOver33_33Ms;
    private long framesOver50Ms;

    void record(double frameTimeMs) {
        if (!Double.isFinite(frameTimeMs) || frameTimeMs <= 0.0D) return;

        int low = 0;
        int high = UPPER_MS.length - 1;
        int bucket = UPPER_MS.length;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (frameTimeMs <= UPPER_MS[mid]) {
                bucket = mid;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }

        counts[bucket]++;
        samples++;
        maxObservedMs = Math.max(maxObservedMs, frameTimeMs);
        if (frameTimeMs > 16.67D) framesOver16_67Ms++;
        if (frameTimeMs > 25.0D) framesOver25Ms++;
        if (frameTimeMs > 33.33D) framesOver33_33Ms++;
        if (frameTimeMs > 50.0D) framesOver50Ms++;
    }

    Snapshot snapshot() {
        if (samples <= 0L) return Snapshot.EMPTY;
        return new Snapshot(
                percentile(0.50D),
                percentile(0.90D),
                percentile(0.95D),
                percentile(0.99D),
                percentile(0.999D),
                maxObservedMs,
                framesOver16_67Ms,
                framesOver25Ms,
                framesOver33_33Ms,
                framesOver50Ms,
                samples
        );
    }

    void reset() {
        java.util.Arrays.fill(counts, 0L);
        samples = 0L;
        maxObservedMs = 0.0D;
        framesOver16_67Ms = 0L;
        framesOver25Ms = 0L;
        framesOver33_33Ms = 0L;
        framesOver50Ms = 0L;
    }

    private double percentile(double fraction) {
        long rank = Math.max(1L, (long) Math.ceil(samples * fraction));
        long cumulative = 0L;
        for (int index = 0; index < counts.length; index++) {
            cumulative += counts[index];
            if (cumulative < rank) continue;
            return index < UPPER_MS.length ? UPPER_MS[index] : maxObservedMs;
        }
        return maxObservedMs;
    }

    record Snapshot(
            double p50Ms,
            double p90Ms,
            double p95Ms,
            double p99Ms,
            double p999Ms,
            double maxMs,
            long framesOver16_67Ms,
            long framesOver25Ms,
            long framesOver33_33Ms,
            long framesOver50Ms,
            long samples
    ) {
        private static final Snapshot EMPTY =
                new Snapshot(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0L, 0L, 0L, 0L, 0L);
    }
}
