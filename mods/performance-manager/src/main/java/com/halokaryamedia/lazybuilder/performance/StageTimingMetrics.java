package com.halokaryamedia.lazybuilder.performance;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/**
 * Opt-in CPU stage timings for proof/diagnostics.
 *
 * Callers should guard nanoTime sampling with enabled() so normal gameplay pays only
 * the branch cost and no timer cost.
 */
public final class StageTimingMetrics {
    public enum Stage {
        ENTITY_CULLING,
        BLOCK_ENTITY_CULLING,
        CHUNK_UPLOAD,
        TERRAIN_SUBMISSION,
        SHADOW,
        COMPOSITE,
        FINAL
    }

    private static final Map<Stage, Counter> COUNTERS = new EnumMap<>(Stage.class);
    private static volatile boolean enabled =
            Boolean.getBoolean("lazybuilder.performance.metrics")
                    || Boolean.getBoolean("lazybuilder.performance.proof");

    static {
        for (Stage stage : Stage.values()) COUNTERS.put(stage, new Counter());
    }

    private StageTimingMetrics() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void enable() {
        enabled = true;
    }

    public static void record(Stage stage, long elapsedNanos) {
        if (!enabled || stage == null || elapsedNanos <= 0L) return;
        COUNTERS.get(stage).record(elapsedNanos);
    }

    public static Snapshot snapshot(Stage stage) {
        Counter counter = COUNTERS.get(stage);
        if (counter == null) return Snapshot.EMPTY;
        return counter.snapshot();
    }

    /**
     * Clears accumulated stage evidence when the active world/session changes.
     *
     * Instrumentation enablement is intentionally preserved: proof/metrics mode is
     * process policy, while samples belong to one gameplay session.
     */
    public static void resetSession() {
        COUNTERS.values().forEach(Counter::reset);
    }

    static void resetForTest() {
        enabled = true;
        COUNTERS.values().forEach(Counter::reset);
    }

    static void setEnabledForTest(boolean value) {
        enabled = value;
    }

    public record Snapshot(long samples, long totalNanos, long maxNanos) {
        private static final Snapshot EMPTY = new Snapshot(0L, 0L, 0L);

        public double averageMs() {
            return samples <= 0L ? 0.0D : (totalNanos / (double) samples) / 1_000_000.0D;
        }

        public double maxMs() {
            return maxNanos / 1_000_000.0D;
        }
    }

    private static final class Counter {
        private final LongAdder samples = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final LongAccumulator maxNanos = new LongAccumulator(Long::max, 0L);

        void record(long elapsedNanos) {
            samples.increment();
            totalNanos.add(elapsedNanos);
            maxNanos.accumulate(elapsedNanos);
        }

        Snapshot snapshot() {
            return new Snapshot(samples.sum(), totalNanos.sum(), maxNanos.get());
        }

        void reset() {
            samples.reset();
            totalNanos.reset();
            maxNanos.reset();
        }
    }
}
