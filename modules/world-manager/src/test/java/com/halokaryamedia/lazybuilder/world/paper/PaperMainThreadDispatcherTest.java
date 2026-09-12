package com.halokaryamedia.lazybuilder.world.paper;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperMainThreadDispatcherTest {
    @Test
    void executesInlineWhenAlreadyOnPrimaryThread() throws Exception {
        PaperMainThreadDispatcher dispatcher = new PaperMainThreadDispatcher(
                new PaperMainThreadDispatcher.SchedulerBridge() {
                    @Override public boolean isPrimaryThread() { return true; }
                    @Override public <T> Future<T> submit(Callable<T> action) {
                        throw new AssertionError("primary-thread calls must not be scheduled");
                    }
                },
                Duration.ofSeconds(1)
        );

        assertEquals("ok", dispatcher.call(() -> "ok"));
    }

    @Test
    void preservesServiceExceptionFromScheduledCall() {
        PaperMainThreadDispatcher dispatcher = new PaperMainThreadDispatcher(
                new PaperMainThreadDispatcher.SchedulerBridge() {
                    @Override public boolean isPrimaryThread() { return false; }
                    @Override public <T> Future<T> submit(Callable<T> action) {
                        CompletableFuture<T> future = new CompletableFuture<>();
                        future.completeExceptionally(new IllegalArgumentException("boom"));
                        return future;
                    }
                },
                Duration.ofSeconds(1)
        );

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> dispatcher.call(() -> "unused")
        );
        assertEquals("boom", failure.getMessage());
    }

    @Test
    void timeoutCancelsQueuedFutureAndReturnsStableError() {
        AtomicReference<Future<?>> submitted = new AtomicReference<>();
        PaperMainThreadDispatcher dispatcher = new PaperMainThreadDispatcher(
                new PaperMainThreadDispatcher.SchedulerBridge() {
                    @Override public boolean isPrimaryThread() { return false; }
                    @Override public <T> Future<T> submit(Callable<T> action) {
                        CompletableFuture<T> future = new CompletableFuture<>();
                        submitted.set(future);
                        return future;
                    }
                },
                Duration.ofMillis(20)
        );

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> dispatcher.call(() -> "unused")
        );
        assertTrue(failure.getMessage().contains("timed out"));
        assertTrue(submitted.get().isCancelled());
    }
}
