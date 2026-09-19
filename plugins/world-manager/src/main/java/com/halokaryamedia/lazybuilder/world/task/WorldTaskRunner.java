package com.halokaryamedia.lazybuilder.world.task;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bounded asynchronous execution owner for long World-Manager operations.
 *
 * <p>The runner owns worker threads and one bounded queue only. It does not own world business
 * logic or filesystem mutation; callers supply one {@link WorldTaskWork} that delegates to
 * canonical services. Overload is rejected instead of allowing an unbounded backlog.</p>
 */
public final class WorldTaskRunner implements AutoCloseable {
    public static final int DEFAULT_WORKERS = 2;
    public static final int DEFAULT_QUEUE_CAPACITY = 32;
    public static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private final WorldTaskRegistry registry;
    private final ThreadPoolExecutor executor;
    private final Duration shutdownTimeout;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<WorldId> queuedOrRunningWorlds = new HashSet<>();

    public WorldTaskRunner(WorldTaskRegistry registry) {
        this(registry, DEFAULT_WORKERS, DEFAULT_QUEUE_CAPACITY, DEFAULT_SHUTDOWN_TIMEOUT);
    }

    WorldTaskRunner(WorldTaskRegistry registry, int workers, Duration shutdownTimeout) {
        this(registry, workers, DEFAULT_QUEUE_CAPACITY, shutdownTimeout);
    }

    WorldTaskRunner(WorldTaskRegistry registry, int workers, int queueCapacity, Duration shutdownTimeout) {
        this.registry = Objects.requireNonNull(registry, "registry");
        if (workers < 1 || workers > 8) {
            throw new IllegalArgumentException("workers must be in range 1..8");
        }
        if (queueCapacity < 1 || queueCapacity > 256) {
            throw new IllegalArgumentException("queueCapacity must be in range 1..256");
        }
        this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
        if (shutdownTimeout.isNegative() || shutdownTimeout.isZero()) {
            throw new IllegalArgumentException("shutdownTimeout must be positive");
        }

        this.executor = new ThreadPoolExecutor(
                workers,
                workers,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofVirtual().name("lazybuilder-world-task-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public WorldTaskSnapshot submit(WorldTaskType type, WorldId worldId, String message, WorldTaskWork work) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(work, "work");

        reserveWorld(worldId);
        WorldTaskSnapshot queued = null;
        try {
            if (closed.get()) {
                throw new IllegalStateException("World task runner is closed");
            }

            queued = registry.create(type, worldId, message);
            SubmittedTask submitted = new SubmittedTask(queued.taskId(), worldId, work);
            executor.execute(submitted);
            return queued;
        } catch (RejectedExecutionException exception) {
            releaseWorld(worldId);
            if (queued != null) {
                if (closed.get() || executor.isShutdown()) {
                    registry.fail(
                            queued.taskId(),
                            "Task runner is shutting down.",
                            "Task could not be scheduled because World-Manager is stopping."
                    );
                    throw new IllegalStateException("World task runner is closed", exception);
                }
                registry.fail(
                        queued.taskId(),
                        "Task queue is full.",
                        "Task could not be scheduled because World-Manager is busy."
                );
            }
            throw new IllegalStateException("World task runner queue is full", exception);
        } catch (RuntimeException failure) {
            releaseWorld(worldId);
            throw failure;
        }
    }

    private void execute(UUID taskId, WorldId worldId, WorldTaskWork work) {
        try {
            registry.markRunning(taskId, "Task started.");
            try {
                String result = work.run((progress, message) ->
                        registry.updateProgress(taskId, progress, message));
                String finalMessage = registry.find(taskId)
                        .map(WorldTaskSnapshot::message)
                        .filter(message -> !message.isBlank())
                        .orElse("Task completed.");
                registry.succeed(taskId, result == null ? "" : result, finalMessage);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                registry.fail(taskId, "Task interrupted.", "Task was interrupted during shutdown.");
            } catch (Exception exception) {
                registry.fail(taskId, safeError(exception), "Task failed.");
            }
        } finally {
            releaseWorld(worldId);
        }
    }

    private synchronized void reserveWorld(WorldId worldId) {
        if (worldId == null) return;
        if (closed.get()) {
            throw new IllegalStateException("World task runner is closed");
        }
        if (!queuedOrRunningWorlds.add(worldId)) {
            throw new IllegalStateException("World already has a queued or running task: " + worldId);
        }
    }

    private synchronized void releaseWorld(WorldId worldId) {
        if (worldId != null) queuedOrRunningWorlds.remove(worldId);
    }

    private static String safeError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        return exception.getClass().getSimpleName() + ": " + message;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdown();
        }
        try {
            if (executor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return;
            }

            failDropped(executor.shutdownNow());
            if (!executor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(
                        "World task runner did not terminate after forced shutdown; "
                                + executor.getActiveCount() + " task(s) are still active."
                );
            }
        } catch (InterruptedException exception) {
            failDropped(executor.shutdownNow());
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for World task runner shutdown.",
                    exception
            );
        }
    }

    private void failDropped(List<Runnable> dropped) {
        for (Runnable runnable : dropped) {
            if (!(runnable instanceof SubmittedTask task)) continue;
            releaseWorld(task.worldId);
            registry.find(task.taskId).ifPresent(snapshot -> {
                if (snapshot.state() == WorldTaskState.QUEUED) {
                    registry.fail(
                            task.taskId,
                            "Task cancelled during World-Manager shutdown.",
                            "Queued task was not started before shutdown."
                    );
                }
            });
        }
    }

    private final class SubmittedTask implements Runnable {
        private final UUID taskId;
        private final WorldId worldId;
        private final WorldTaskWork work;

        private SubmittedTask(UUID taskId, WorldId worldId, WorldTaskWork work) {
            this.taskId = taskId;
            this.worldId = worldId;
            this.work = work;
        }

        @Override
        public void run() {
            execute(taskId, worldId, work);
        }
    }
}
