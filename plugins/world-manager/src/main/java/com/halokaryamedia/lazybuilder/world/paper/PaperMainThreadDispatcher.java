package com.halokaryamedia.lazybuilder.world.paper;

import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Single boundary for invoking Paper/Bukkit mutations from non-primary threads.
 *
 * <p>The normal dispatch path is interruption-sensitive. Cleanup dispatch deliberately defers
 * interruption until the scheduled Paper cleanup either completes or reaches the same bounded
 * timeout, so cancellation cannot strand request-bound world tasks in a prepared state.</p>
 */
public final class PaperMainThreadDispatcher {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final SchedulerBridge scheduler;
    private final Duration timeout;

    public PaperMainThreadDispatcher(JavaPlugin plugin) {
        this(new BukkitSchedulerBridge(Objects.requireNonNull(plugin, "plugin")), DEFAULT_TIMEOUT);
    }

    PaperMainThreadDispatcher(SchedulerBridge scheduler, Duration timeout) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public <T> T call(Callable<T> action) throws Exception {
        Objects.requireNonNull(action, "action");
        if (scheduler.isPrimaryThread()) {
            return action.call();
        }

        Dispatch<T> dispatch = submitTracked(action);
        try {
            return dispatch.outcome.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            if (cancelBeforeStart(dispatch)) {
                throw timeoutFailure(exception);
            }
            return awaitAlreadyStarted(dispatch.outcome, false);
        } catch (InterruptedException exception) {
            if (cancelBeforeStart(dispatch)) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted while waiting for Paper main-thread dispatch",
                        exception);
            }
            // Paper already started (or completed) the callable. Keep ownership until
            // the real outcome is known instead of reporting failure while a mutation
            // can still complete later.
            return awaitAlreadyStarted(dispatch.outcome, true);
        } catch (ExecutionException exception) {
            return rethrowExecution(exception);
        }
    }

    /**
     * Bounded cleanup dispatch that cannot be skipped merely because the worker was cancelled.
     * Any interrupt observed while waiting is restored on the worker after cleanup resolves.
     */
    public <T> T callCleanup(Callable<T> action) throws Exception {
        Objects.requireNonNull(action, "action");
        if (scheduler.isPrimaryThread()) {
            return action.call();
        }

        Dispatch<T> dispatch = submitTracked(action);
        long deadline = System.nanoTime() + timeout.toNanos();
        boolean interrupted = Thread.interrupted();
        try {
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) {
                    TimeoutException timeoutException =
                            new TimeoutException("cleanup deadline reached");
                    if (cancelBeforeStart(dispatch)) {
                        throw timeoutFailure(timeoutException);
                    }
                    return awaitAlreadyStarted(dispatch.outcome, interrupted);
                }
                try {
                    return dispatch.outcome.get(remaining, TimeUnit.NANOSECONDS);
                } catch (InterruptedException ignored) {
                    interrupted = true;
                } catch (TimeoutException exception) {
                    if (cancelBeforeStart(dispatch)) {
                        throw timeoutFailure(exception);
                    }
                    return awaitAlreadyStarted(dispatch.outcome, interrupted);
                } catch (ExecutionException exception) {
                    return rethrowExecution(exception);
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private <T> Dispatch<T> submitTracked(Callable<T> action) {
        AtomicReference<DispatchState> state =
                new AtomicReference<>(DispatchState.QUEUED);
        CompletableFuture<T> outcome = new CompletableFuture<>();
        Future<?> ticket = scheduler.submit(() -> {
            if (!state.compareAndSet(DispatchState.QUEUED, DispatchState.RUNNING)) {
                CancellationException cancelled =
                        new CancellationException("Paper dispatch was cancelled before start");
                outcome.completeExceptionally(cancelled);
                throw cancelled;
            }
            try {
                T result = action.call();
                outcome.complete(result);
                return result;
            } catch (Throwable failure) {
                outcome.completeExceptionally(failure);
                if (failure instanceof Exception exception) throw exception;
                if (failure instanceof Error error) throw error;
                throw new IllegalStateException(failure);
            }
        });
        return new Dispatch<>(ticket, state, outcome);
    }

    private static boolean cancelBeforeStart(Dispatch<?> dispatch) {
        if (!dispatch.state.compareAndSet(DispatchState.QUEUED, DispatchState.CANCELLED)) {
            return false;
        }
        // Ticket cancellation is best-effort only. The state transition above is the
        // execution authority: a wrapper that runs later cannot enter RUNNING.
        dispatch.ticket.cancel(false);
        return true;
    }

    private static <T> T awaitAlreadyStarted(
            Future<T> future,
            boolean interruptedBeforeWait
    ) throws Exception {
        boolean interrupted = interruptedBeforeWait;
        try {
            while (true) {
                try {
                    return future.get();
                } catch (InterruptedException ignored) {
                    interrupted = true;
                } catch (ExecutionException exception) {
                    return rethrowExecution(exception);
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private IllegalStateException timeoutFailure(TimeoutException exception) {
        return new IllegalStateException(
                "Paper main-thread dispatch timed out after " + timeout.toMillis() + " ms",
                exception
        );
    }

    private static <T> T rethrowExecution(ExecutionException exception) throws Exception {
        Throwable cause = exception.getCause();
        if (cause instanceof Exception checked) throw checked;
        if (cause instanceof Error error) throw error;
        throw new IllegalStateException("Paper main-thread dispatch failed", cause);
    }

    private enum DispatchState { QUEUED, RUNNING, CANCELLED }

    private record Dispatch<T>(
            Future<?> ticket,
            AtomicReference<DispatchState> state,
            CompletableFuture<T> outcome
    ) { }

    interface SchedulerBridge {
        boolean isPrimaryThread();
        <T> Future<T> submit(Callable<T> action);
    }

    private record BukkitSchedulerBridge(JavaPlugin plugin) implements SchedulerBridge {
        @Override
        public boolean isPrimaryThread() {
            return plugin.getServer().isPrimaryThread();
        }

        @Override
        public <T> Future<T> submit(Callable<T> action) {
            return plugin.getServer().getScheduler().callSyncMethod(plugin, action);
        }
    }
}
