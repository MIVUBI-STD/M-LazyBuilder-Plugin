package com.halokaryamedia.lazybuilder.world.paper;

import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Single boundary for invoking Paper/Bukkit mutations from non-primary threads.
 *
 * <p>The dispatcher preserves the original service exception, cancels a timed-out queued
 * future, and restores the interrupted flag when a waiting worker is interrupted.</p>
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

        Future<T> future = scheduler.submit(action);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(false);
            throw new IllegalStateException(
                    "Paper main-thread dispatch timed out after " + timeout.toMillis() + " ms",
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Paper main-thread dispatch", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) throw checked;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Paper main-thread dispatch failed", cause);
        }
    }

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
