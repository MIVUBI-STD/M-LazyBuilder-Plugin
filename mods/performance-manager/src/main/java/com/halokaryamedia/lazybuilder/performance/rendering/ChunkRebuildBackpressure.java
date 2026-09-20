package com.halokaryamedia.lazybuilder.performance.rendering;

import com.halokaryamedia.lazybuilder.performance.FramePressure;
import net.minecraft.client.render.chunk.ChunkBuilder;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Holds only non-prioritized chunk tasks during verified heavy client pressure.
 *
 * Tasks are never discarded. The queue is bounded and fails open when full, prioritized tasks always
 * bypass it, and at least one deferred task is released per tick even under sustained heavy pressure.
 *
 * Registry locking is intentionally narrow: each builder owns its own queue state, so queue operations
 * do not serialize unrelated ChunkBuilder instances.
 */
public final class ChunkRebuildBackpressure {
    private static final Map<ChunkBuilder, DeferredState> STATES = new IdentityHashMap<>();
    private static final ThreadLocal<Boolean> RELEASING = ThreadLocal.withInitial(() -> false);

    private ChunkRebuildBackpressure() {
    }

    public static boolean deferIfNeeded(
            ChunkBuilder builder,
            ChunkBuilder.BuiltChunk.Task task,
            FramePressure pressure
    ) {
        if (builder == null
                || task == null
                || pressure != FramePressure.HEAVY
                || task.isPrioritized()
                || Boolean.TRUE.equals(RELEASING.get())) {
            return false;
        }

        DeferredState state = stateIfPresent(builder);
        int deferredTasks = state == null ? 0 : state.size();
        if (!ChunkRebuildBackpressurePolicy.shouldDefer(
                pressure,
                false,
                builder.getToBatchCount(),
                builder.getFreeBufferCount(),
                deferredTasks
        )) {
            return false;
        }

        state = stateFor(builder);
        state.add(task);
        ChunkPipelineMetrics.recordRebuildBackpressureDeferral();
        return true;
    }

    public static void drain(ChunkBuilder builder, FramePressure pressure) {
        if (builder == null) return;

        int budget = ChunkRebuildBackpressurePolicy.releaseBudget(pressure);
        for (int released = 0; released < budget; released++) {
            DeferredState state = stateIfPresent(builder);
            if (state == null) return;

            ChunkBuilder.BuiltChunk.Task task = state.poll();
            if (task == null) {
                removeIfEmpty(builder, state);
                return;
            }
            removeIfEmpty(builder, state);

            RELEASING.set(true);
            try {
                builder.send(task);
                ChunkPipelineMetrics.recordRebuildBackpressureRelease();
            } finally {
                RELEASING.set(false);
            }
        }
    }

    public static void releaseAll(ChunkBuilder builder) {
        if (builder == null) return;

        DeferredState state = stateIfPresent(builder);
        if (state == null) return;

        while (true) {
            ChunkBuilder.BuiltChunk.Task task = state.poll();
            if (task == null) {
                removeIfEmpty(builder, state);
                return;
            }

            RELEASING.set(true);
            try {
                builder.send(task);
                ChunkPipelineMetrics.recordRebuildBackpressureRelease();
            } finally {
                RELEASING.set(false);
            }
        }
    }

    public static void cancel(ChunkBuilder builder) {
        if (builder == null) return;

        DeferredState state;
        synchronized (STATES) {
            state = STATES.remove(builder);
        }
        if (state == null) return;

        ChunkBuilder.BuiltChunk.Task task;
        while ((task = state.poll()) != null) {
            task.cancel();
        }
    }

    static int deferredCount(ChunkBuilder builder) {
        DeferredState state = stateIfPresent(builder);
        return state == null ? 0 : state.size();
    }

    private static DeferredState stateFor(ChunkBuilder builder) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(builder, ignored -> new DeferredState());
        }
    }

    private static DeferredState stateIfPresent(ChunkBuilder builder) {
        synchronized (STATES) {
            return STATES.get(builder);
        }
    }

    private static void removeIfEmpty(ChunkBuilder builder, DeferredState state) {
        if (!state.isEmpty()) return;
        synchronized (STATES) {
            if (STATES.get(builder) == state && state.isEmpty()) {
                STATES.remove(builder);
            }
        }
    }

    private static final class DeferredState {
        private final ArrayDeque<ChunkBuilder.BuiltChunk.Task> queue = new ArrayDeque<>();

        synchronized void add(ChunkBuilder.BuiltChunk.Task task) {
            queue.addLast(task);
        }

        synchronized ChunkBuilder.BuiltChunk.Task poll() {
            return queue.pollFirst();
        }

        synchronized int size() {
            return queue.size();
        }

        synchronized boolean isEmpty() {
            return queue.isEmpty();
        }
    }
}
