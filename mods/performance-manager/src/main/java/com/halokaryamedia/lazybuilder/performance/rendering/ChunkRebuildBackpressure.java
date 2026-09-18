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
 */
public final class ChunkRebuildBackpressure {
    private static final Map<ChunkBuilder, ArrayDeque<ChunkBuilder.BuiltChunk.Task>> DEFERRED =
            new IdentityHashMap<>();
    private static final ThreadLocal<Boolean> RELEASING = ThreadLocal.withInitial(() -> false);

    private ChunkRebuildBackpressure() {
    }

    public static synchronized boolean deferIfNeeded(
            ChunkBuilder builder,
            ChunkBuilder.BuiltChunk.Task task,
            FramePressure pressure
    ) {
        if (builder == null || task == null || Boolean.TRUE.equals(RELEASING.get())) return false;

        ArrayDeque<ChunkBuilder.BuiltChunk.Task> queue = DEFERRED.computeIfAbsent(
                builder,
                ignored -> new ArrayDeque<>()
        );
        if (!ChunkRebuildBackpressurePolicy.shouldDefer(
                pressure,
                task.isPrioritized(),
                builder.getToBatchCount(),
                builder.getFreeBufferCount(),
                queue.size()
        )) {
            if (queue.isEmpty()) DEFERRED.remove(builder);
            return false;
        }

        queue.addLast(task);
        ChunkPipelineMetrics.recordRebuildBackpressureDeferral();
        return true;
    }

    public static void drain(ChunkBuilder builder, FramePressure pressure) {
        if (builder == null) return;

        int budget = ChunkRebuildBackpressurePolicy.releaseBudget(pressure);
        for (int released = 0; released < budget; released++) {
            ChunkBuilder.BuiltChunk.Task task;
            synchronized (ChunkRebuildBackpressure.class) {
                ArrayDeque<ChunkBuilder.BuiltChunk.Task> queue = DEFERRED.get(builder);
                if (queue == null) return;
                task = queue.pollFirst();
                if (queue.isEmpty()) DEFERRED.remove(builder);
            }
            if (task == null) return;

            RELEASING.set(true);
            try {
                builder.send(task);
                ChunkPipelineMetrics.recordRebuildBackpressureRelease();
            } finally {
                RELEASING.set(false);
            }
        }
    }

    public static synchronized void cancel(ChunkBuilder builder) {
        if (builder == null) return;
        ArrayDeque<ChunkBuilder.BuiltChunk.Task> queue = DEFERRED.remove(builder);
        if (queue == null) return;
        ChunkBuilder.BuiltChunk.Task task;
        while ((task = queue.pollFirst()) != null) {
            task.cancel();
        }
    }

    static synchronized int deferredCount(ChunkBuilder builder) {
        ArrayDeque<ChunkBuilder.BuiltChunk.Task> queue = DEFERRED.get(builder);
        return queue == null ? 0 : queue.size();
    }
}
