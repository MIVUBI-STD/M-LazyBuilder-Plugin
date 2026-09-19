package com.halokaryamedia.lazybuilder.world.paper;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void timeoutWaitsForAlreadyRunningPaperMutationInsteadOfReportingFalseFailure() throws Exception {
        CountDownLatch actionStarted = new CountDownLatch(1);
        CountDownLatch releaseAction = new CountDownLatch(1);
        PaperMainThreadDispatcher dispatcher = new PaperMainThreadDispatcher(
                new PaperMainThreadDispatcher.SchedulerBridge() {
                    @Override public boolean isPrimaryThread() { return false; }
                    @Override public <T> Future<T> submit(Callable<T> action) {
                        FutureTask<T> future = new FutureTask<>(action);
                        Thread.ofPlatform().start(future);
                        return future;
                    }
                },
                Duration.ofMillis(20)
        );

        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = Thread.ofPlatform().start(() -> {
            try {
                result.set(dispatcher.call(() -> {
                    actionStarted.countDown();
                    releaseAction.await();
                    return "committed";
                }));
            } catch (Throwable error) {
                failure.set(error);
            }
        });

        assertTrue(actionStarted.await(1, TimeUnit.SECONDS));
        Thread.sleep(40);
        assertTrue(worker.isAlive(),
                "ownership must be retained while the already-running Paper action is unresolved");

        releaseAction.countDown();
        worker.join(1_000);

        assertFalse(worker.isAlive());
        assertEquals("committed", result.get());
        assertEquals(null, failure.get());
    }

    @Test
    void interruptedAlreadyRunningDispatchWaitsForRealOutcomeAndRestoresInterrupt() throws Exception {
        CountDownLatch actionStarted = new CountDownLatch(1);
        CountDownLatch releaseAction = new CountDownLatch(1);
        PaperMainThreadDispatcher dispatcher = new PaperMainThreadDispatcher(
                new PaperMainThreadDispatcher.SchedulerBridge() {
                    @Override public boolean isPrimaryThread() { return false; }
                    @Override public <T> Future<T> submit(Callable<T> action) {
                        FutureTask<T> future = new FutureTask<>(action);
                        Thread.ofPlatform().start(future);
                        return future;
                    }
                },
                Duration.ofSeconds(5)
        );

        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>(false);
        Thread worker = Thread.ofPlatform().start(() -> {
            try {
                result.set(dispatcher.call(() -> {
                    actionStarted.countDown();
                    releaseAction.await();
                    return "committed";
                }));
                interrupted.set(Thread.currentThread().isInterrupted());
            } catch (Throwable ignored) {
            }
        });

        assertTrue(actionStarted.await(1, TimeUnit.SECONDS));
        worker.interrupt();
        Thread.sleep(20);
        assertTrue(worker.isAlive());

        releaseAction.countDown();
        worker.join(1_000);

        assertEquals("committed", result.get());
        assertTrue(interrupted.get());
    }

    @Test
    void interruptedNormalDispatchCancelsQueuedPaperMutation() throws Exception {
        AtomicReference<Future<?>> submitted = new AtomicReference<>();
        CountDownLatch scheduled = new CountDownLatch(1);
        PaperMainThreadDispatcher dispatcher = new PaperMainThreadDispatcher(
                new PaperMainThreadDispatcher.SchedulerBridge() {
                    @Override public boolean isPrimaryThread() { return false; }
                    @Override public <T> Future<T> submit(Callable<T> action) {
                        CompletableFuture<T> future = new CompletableFuture<>();
                        submitted.set(future);
                        scheduled.countDown();
                        return future;
                    }
                },
                Duration.ofSeconds(5)
        );

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = Thread.ofPlatform().start(() -> {
            try {
                dispatcher.call(() -> "must-not-run");
            } catch (Throwable error) {
                failure.set(error);
            }
        });

        assertTrue(scheduled.await(1, TimeUnit.SECONDS));
        worker.interrupt();
        worker.join(1_000);

        assertFalse(worker.isAlive());
        assertTrue(submitted.get().isCancelled(),
                "interrupted owner must cancel the queued Paper callable");
        assertTrue(failure.get() instanceof IllegalStateException);
    }

    @Test
    void cleanupDispatchDefersExistingInterruptUntilCleanupCompletes() throws Exception {
        AtomicReference<Callable<?>> submittedAction = new AtomicReference<>();
        PaperMainThreadDispatcher dispatcher = new PaperMainThreadDispatcher(
                new PaperMainThreadDispatcher.SchedulerBridge() {
                    @Override public boolean isPrimaryThread() { return false; }
                    @Override public <T> Future<T> submit(Callable<T> action) {
                        submittedAction.set(action);
                        CompletableFuture<T> future = new CompletableFuture<>();
                        try {
                            future.complete(action.call());
                        } catch (Exception exception) {
                            future.completeExceptionally(exception);
                        }
                        return future;
                    }
                },
                Duration.ofSeconds(1)
        );

        Thread.currentThread().interrupt();
        try {
            assertEquals("cleaned", dispatcher.callCleanup(() -> "cleaned"));
            assertTrue(Thread.currentThread().isInterrupted(), "cleanup must restore the caller interrupt flag");
            assertFalse(submittedAction.get() == null, "cleanup must still be submitted while caller is interrupted");
        } finally {
            Thread.interrupted();
        }
    }
}
