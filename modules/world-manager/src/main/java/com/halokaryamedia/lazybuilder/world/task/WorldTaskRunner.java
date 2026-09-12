package com.halokaryamedia.lazybuilder.world.task;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bounded asynchronous execution owner for long World-Manager operations.
 *
 * <p>The runner owns worker threads only. It does not own world business logic or filesystem
 * mutation; callers supply one {@link WorldTaskWork} that delegates to canonical services.</p>
 */
public final class WorldTaskRunner implements AutoCloseable {
    public static final int DEFAULT_WORKERS = 2;
    public static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private final WorldTaskRegistry registry;
    private final ExecutorService executor;
    private final Duration shutdownTimeout;
    private final AtomicBoolean closed = new AtomicBoolean();

    public WorldTaskRunner(WorldTaskRegistry registry) {
        this(registry, DEFAULT_WORKERS, DEFAULT_SHUTDOWN_TIMEOUT);
    }

    WorldTaskRunner(WorldTaskRegistry registry, int workers, Duration shutdownTimeout) {
        this.registry = Objects.requireNonNull(registry, "registry");
        if (workers < 1 || workers > 8) {
            throw new IllegalArgumentException("workers must be in range 1..8");
        }
        this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
        if (shutdownTimeout.isNegative() || shutdownTimeout.isZero()) {
            throw new IllegalArgumentException("shutdownTimeout must be positive");
        }
        this.executor = Executors.newFixedThreadPool(workers, Thread.ofVirtual()
                .name("lazybuilder-world-task-", 0)
                .factory());
    }

    public WorldTaskSnapshot submit(WorldTaskType type, WorldId worldId, String message, WorldTaskWork work) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(work, "work");
        if (closed.get()) {
            throw new IllegalStateException("World task runner is closed");
        }

        WorldTaskSnapshot queued = registry.create(type, worldId, message);
        try {
            executor.execute(() -> execute(queued.taskId(), work));
            return queued;
        } catch (RejectedExecutionException exception) {
            registry.markRunning(queued.taskId(), "Task runner rejected the queued task.");
            registry.fail(queued.taskId(), exception.toString(), "Task could not be scheduled.");
            throw new IllegalStateException("World task runner rejected task " + queued.taskId(), exception);
        }
    }

    private void execute(UUID taskId, WorldTaskWork work) {
        registry.markRunning(taskId, "Task started.");
        try {
            String result = work.run((progress, message) -> registry.updateProgress(taskId, progress, message));
            registry.succeed(taskId, result == null ? "" : result, "Task completed.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            registry.fail(taskId, "Task interrupted.", "Task was interrupted during shutdown.");
        } catch (Exception exception) {
            registry.fail(taskId, safeError(exception), "Task failed.");
        }
    }

    private static String safeError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        return exception.getClass().getSimpleName() + ": " + message;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
                executor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
