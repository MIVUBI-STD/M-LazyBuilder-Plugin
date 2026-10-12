package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the actual bounded per-builder queue, without Minecraft task mocks. */
final class ChunkRebuildBackpressureStateTest {
    @Test
    void closePreventsStaleProducersFromSubmittingWorkToDetachedState() {
        var state = new ChunkRebuildBackpressure.DeferredState<Integer>();
        assertTrue(state.addIfCapacity(1, 3));
        assertFalse(state.closeIfEmpty(), "pending work must not be detached");
        assertEquals(1, state.poll());
        assertTrue(state.closeIfEmpty(), "empty state can now detach safely");
        assertFalse(state.addIfCapacity(2, 3), "stale producer must fail open");
        assertNull(state.poll());
    }

    @Test
    void cancelClosureRejectsNewWorkAndRetainsOldWorkForCleanup() {
        var state = new ChunkRebuildBackpressure.DeferredState<Integer>();
        assertTrue(state.addIfCapacity(1, 3));
        state.close();
        assertFalse(state.addIfCapacity(2, 3));
        assertEquals(1, state.poll(), "existing work remains available for cancellation");
        assertNull(state.poll());
    }

    @Test
    void concurrentSendsNeverExceedQueueCapacity() throws Exception {
        int capacity = ChunkRebuildBackpressurePolicy.MAX_DEFERRED_TASKS;
        var state = new ChunkRebuildBackpressure.DeferredState<Integer>();
        var accepted = new AtomicInteger();
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(8)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int producer = 0; producer < 8; producer++) {
                int producerId = producer;
                futures.add(workers.submit(() -> {
                    start.await();
                    for (int i = 0; i < 100; i++) {
                        if (state.addIfCapacity(producerId * 100 + i, capacity)) {
                            accepted.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var result : futures) result.get(10, TimeUnit.SECONDS);
        }
        assertEquals(capacity, accepted.get());
        assertEquals(capacity, state.size());
        assertFalse(state.addIfCapacity(999, capacity));
        var unique = new java.util.HashSet<Integer>();
        Integer item;
        while ((item = state.poll()) != null) assertTrue(unique.add(item));
        assertEquals(capacity, unique.size());
        assertTrue(state.closeIfEmpty());
    }
}
