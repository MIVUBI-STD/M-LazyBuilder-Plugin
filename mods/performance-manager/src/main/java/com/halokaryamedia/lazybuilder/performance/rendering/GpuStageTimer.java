package com.halokaryamedia.lazybuilder.performance.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;

import java.util.EnumMap;
import java.util.Map;

/**
 * Non-blocking GPU timer-query sampler.
 *
 * Queries are proof/metrics-only and use a small ring so the CPU never waits for GPU completion.
 */
public final class GpuStageTimer {
    public enum Stage {
        TERRAIN,
        SHADOW,
        POST_PROCESS
    }

    private static final int RING_SIZE = 3;
    private static final Map<Stage, State> STATES = new EnumMap<>(Stage.class);
    private static Stage activeStage;
    private static int activeQuery;
    private static long sessionGeneration = 1L;
    private static volatile boolean enabled =
            Boolean.getBoolean("lazybuilder.performance.metrics")
                    || Boolean.getBoolean("lazybuilder.performance.proof");

    static {
        for (Stage stage : Stage.values()) STATES.put(stage, new State());
    }

    private GpuStageTimer() {
    }

    public static void enable() {
        enabled = true;
    }

    public static boolean enabled() {
        return enabled;
    }

    public static boolean begin(Stage stage) {
        if (!enabled || stage == null || activeStage != null || !RenderSystem.isOnRenderThread()) {
            return false;
        }
        if (!GpuCapabilityProfile.current().timerQueries()) return false;

        State state = STATES.get(stage);
        state.pollAvailable();

        int slot = state.nextFreeSlot();
        if (slot < 0) return false;

        int query = state.ensureQuery(slot);
        GL33C.glBeginQuery(GL33C.GL_TIME_ELAPSED, query);
        state.pending[slot] = true;
        state.pendingGeneration[slot] = sessionGeneration;
        state.cursor = (slot + 1) % RING_SIZE;
        activeStage = stage;
        activeQuery = query;
        return true;
    }

    public static void end(Stage stage) {
        if (activeStage != stage || activeQuery == 0 || !RenderSystem.isOnRenderThread()) return;
        GL33C.glEndQuery(GL33C.GL_TIME_ELAPSED);
        activeStage = null;
        activeQuery = 0;
    }

    /**
     * Frame-boundary guard for exceptional render exits. Completing the query is safer than
     * leaving GL_TIME_ELAPSED active and poisoning later instrumentation.
     */
    public static void recoverStaleQuery() {
        if (activeStage == null || activeQuery == 0 || !RenderSystem.isOnRenderThread()) return;
        GL33C.glEndQuery(GL33C.GL_TIME_ELAPSED);
        activeStage = null;
        activeQuery = 0;
    }

    public static Snapshot snapshot(Stage stage) {
        if (stage == null) return Snapshot.EMPTY;
        if (RenderSystem.isOnRenderThread() && GpuCapabilityProfile.current().timerQueries()) {
            STATES.get(stage).pollAvailable();
        }
        return STATES.get(stage).snapshot();
    }

    /**
     * Starts a new gameplay evidence generation without blocking on outstanding GPU queries.
     * Late results from the previous world are drained but ignored, so benchmark samples do
     * not bleed across teleport/world-session boundaries.
     */
    public static void resetSession() {
        sessionGeneration++;
        if (sessionGeneration <= 0L) sessionGeneration = 1L;
        for (State state : STATES.values()) state.resetCounters();
    }

    static void resetForTest() {
        activeStage = null;
        activeQuery = 0;
        sessionGeneration = 1L;
        for (State state : STATES.values()) state.resetCounters();
    }

    private static final class State {
        private final int[] queries = new int[RING_SIZE];
        private final boolean[] pending = new boolean[RING_SIZE];
        private final long[] pendingGeneration = new long[RING_SIZE];
        private int cursor;
        private long samples;
        private long totalNanos;
        private long maxNanos;
        private long lastNanos;

        int ensureQuery(int slot) {
            int query = queries[slot];
            if (query == 0) {
                query = GL15C.glGenQueries();
                queries[slot] = query;
            }
            return query;
        }

        int nextFreeSlot() {
            for (int offset = 0; offset < RING_SIZE; offset++) {
                int slot = (cursor + offset) % RING_SIZE;
                if (!pending[slot]) return slot;
            }
            return -1;
        }

        void pollAvailable() {
            for (int slot = 0; slot < RING_SIZE; slot++) {
                int query = queries[slot];
                if (!pending[slot] || query == 0) continue;
                if (GL15C.glGetQueryObjecti(query, GL15C.GL_QUERY_RESULT_AVAILABLE) == 0) continue;

                long elapsed = GL33C.glGetQueryObjecti64(query, GL15C.GL_QUERY_RESULT);
                pending[slot] = false;
                long generation = pendingGeneration[slot];
                pendingGeneration[slot] = 0L;
                if (generation != sessionGeneration || elapsed <= 0L) continue;
                samples++;
                totalNanos += elapsed;
                maxNanos = Math.max(maxNanos, elapsed);
                lastNanos = elapsed;
            }
        }

        Snapshot snapshot() {
            return new Snapshot(samples, lastNanos, totalNanos, maxNanos);
        }

        void resetCounters() {
            samples = 0L;
            totalNanos = 0L;
            maxNanos = 0L;
            lastNanos = 0L;
        }
    }

    public record Snapshot(long samples, long lastNanos, long totalNanos, long maxNanos) {
        private static final Snapshot EMPTY = new Snapshot(0L, 0L, 0L, 0L);

        public double lastMs() {
            return lastNanos / 1_000_000.0D;
        }

        public double averageMs() {
            return samples <= 0L ? 0.0D : (totalNanos / (double) samples) / 1_000_000.0D;
        }

        public double maxMs() {
            return maxNanos / 1_000_000.0D;
        }
    }
}
