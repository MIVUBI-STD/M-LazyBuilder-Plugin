package com.halokaryamedia.lazybuilder.world.task;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldTaskRunnerTest {
    @Test
    void runsWorkAndPublishesProgressAndSuccess() throws Exception {
        WorldTaskRegistry registry = new WorldTaskRegistry();
        CountDownLatch completed = new CountDownLatch(1);
        try (WorldTaskRunner runner = new WorldTaskRunner(registry, 1, Duration.ofSeconds(1))) {
            WorldTaskSnapshot queued = runner.submit(WorldTaskType.BACKUP, WorldId.create(), "Queued", progress -> {
                progress.update(40, "Copying");
                completed.countDown();
                return "backup-id";
            });

            assertTrue(completed.await(2, TimeUnit.SECONDS));
            WorldTaskSnapshot finalSnapshot = waitForTerminal(registry, queued);
            assertEquals(WorldTaskState.SUCCEEDED, finalSnapshot.state());
            assertEquals(100, finalSnapshot.progressPercent());
            assertEquals("backup-id", finalSnapshot.result());
        }
    }

    @Test
    void successPreservesFinalProgressMessage() throws Exception {
        WorldTaskRegistry registry = new WorldTaskRegistry();
        try (WorldTaskRunner runner = new WorldTaskRunner(registry, 1, Duration.ofSeconds(1))) {
            WorldTaskSnapshot queued = runner.submit(
                    WorldTaskType.BACKUP,
                    WorldId.create(),
                    "Queued",
                    progress -> {
                        progress.update(99, "Committed with a source restoration warning.");
                        return "backup-id";
                    });

            WorldTaskSnapshot finalSnapshot = waitForTerminal(registry, queued);
            assertEquals(WorldTaskState.SUCCEEDED, finalSnapshot.state());
            assertEquals("Committed with a source restoration warning.", finalSnapshot.message());
            assertEquals("backup-id", finalSnapshot.result());
        }
    }

    @Test
    void failureIsCapturedInRegistry() throws Exception {
        WorldTaskRegistry registry = new WorldTaskRegistry();
        try (WorldTaskRunner runner = new WorldTaskRunner(registry, 1, Duration.ofSeconds(1))) {
            WorldTaskSnapshot queued = runner.submit(WorldTaskType.EXPORT, WorldId.create(), "Queued", progress -> {
                throw new IllegalStateException("boom");
            });
            WorldTaskSnapshot finalSnapshot = waitForTerminal(registry, queued);
            assertEquals(WorldTaskState.FAILED, finalSnapshot.state());
            assertTrue(finalSnapshot.error().contains("boom"));
        }
    }

    @Test
    void rejectsSubmissionAfterClose() {
        WorldTaskRunner runner = new WorldTaskRunner(new WorldTaskRegistry(), 1, Duration.ofSeconds(1));
        runner.close();
        assertThrows(IllegalStateException.class,
                () -> runner.submit(WorldTaskType.DUPLICATE, WorldId.create(), "Queued", progress -> "unused"));
    }

    @Test
    void rejectsDuplicateQueuedOrRunningTaskForSameWorld() throws Exception {
        WorldTaskRegistry registry = new WorldTaskRegistry();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        WorldId worldId = WorldId.create();

        try (WorldTaskRunner runner = new WorldTaskRunner(registry, 1, 2, Duration.ofSeconds(1))) {
            runner.submit(WorldTaskType.BACKUP, worldId, "first", progress -> {
                started.countDown();
                release.await(2, TimeUnit.SECONDS);
                return "done";
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));

            IllegalStateException duplicate = assertThrows(IllegalStateException.class, () ->
                    runner.submit(WorldTaskType.EXPORT, worldId, "duplicate", progress -> "unused"));
            assertTrue(duplicate.getMessage().contains("queued or running task"));
            assertEquals(1, registry.recent().size(),
                    "replayed submission must not create a ghost task entry");

            release.countDown();
            waitForTerminal(registry, registry.recent().getFirst());

            WorldTaskSnapshot next = runner.submit(
                    WorldTaskType.EXPORT, worldId, "next", progress -> "next");
            assertEquals(WorldTaskState.QUEUED, next.state());
        } finally {
            release.countDown();
        }
    }

    @Test
    void rejectsWhenBoundedQueueIsFull() throws Exception {
        WorldTaskRegistry registry = new WorldTaskRegistry();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        try (WorldTaskRunner runner = new WorldTaskRunner(registry, 1, 1, Duration.ofSeconds(1))) {
            runner.submit(WorldTaskType.BACKUP, WorldId.create(), "first", progress -> {
                firstStarted.countDown();
                releaseFirst.await(2, TimeUnit.SECONDS);
                return "first";
            });
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

            runner.submit(WorldTaskType.EXPORT, WorldId.create(), "queued", progress -> "queued");
            IllegalStateException rejection = assertThrows(IllegalStateException.class,
                    () -> runner.submit(WorldTaskType.DUPLICATE, WorldId.create(), "overflow", progress -> "overflow"));
            assertTrue(rejection.getMessage().contains("queue is full"));
            assertTrue(registry.recent().stream().anyMatch(snapshot ->
                    snapshot.state() == WorldTaskState.FAILED && snapshot.error().contains("queue is full")));

            releaseFirst.countDown();
        } finally {
            releaseFirst.countDown();
        }
    }

    @Test
    void forcedShutdownFailsClosedWhenRunningWorkIgnoresInterrupts() throws Exception {
        WorldTaskRegistry registry = new WorldTaskRegistry();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        WorldTaskRunner runner = new WorldTaskRunner(registry, 1, 1, Duration.ofMillis(25));
        try {
            runner.submit(WorldTaskType.BACKUP, WorldId.create(), "stubborn", progress -> {
                started.countDown();
                while (release.getCount() > 0) {
                    try {
                        release.await(10, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException ignored) {
                        // Deliberately simulate non-cooperative work so close() must not
                        // silently claim that shutdown completed.
                    }
                }
                return "finished-late";
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));

            IllegalStateException error = assertThrows(IllegalStateException.class, runner::close);
            assertTrue(error.getMessage().contains("did not terminate"));
        } finally {
            release.countDown();
            // close() is intentionally retryable after a prior timeout.
            runner.close();
        }
    }

    @Test
    void forcedShutdownMarksNeverStartedQueuedTasksFailed() throws Exception {
        WorldTaskRegistry registry = new WorldTaskRegistry();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch holdFirst = new CountDownLatch(1);
        WorldTaskRunner runner = new WorldTaskRunner(registry, 1, 2, Duration.ofMillis(25));
        try {
            runner.submit(WorldTaskType.BACKUP, WorldId.create(), "running", progress -> {
                firstStarted.countDown();
                holdFirst.await();
                return "running";
            });
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

            WorldTaskSnapshot queued = runner.submit(
                    WorldTaskType.EXPORT, WorldId.create(), "queued", progress -> "should-not-run");
            runner.close();

            WorldTaskSnapshot finalSnapshot = registry.find(queued.taskId()).orElseThrow();
            assertEquals(WorldTaskState.FAILED, finalSnapshot.state());
            assertTrue(finalSnapshot.error().contains("shutdown"));
        } finally {
            holdFirst.countDown();
            runner.close();
        }
    }

    private static WorldTaskSnapshot waitForTerminal(WorldTaskRegistry registry, WorldTaskSnapshot queued) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        WorldTaskSnapshot current = queued;
        while (System.nanoTime() < deadline) {
            current = registry.find(queued.taskId()).orElseThrow();
            if (current.state() == WorldTaskState.SUCCEEDED || current.state() == WorldTaskState.FAILED) return current;
            Thread.sleep(10);
        }
        throw new AssertionError("task did not finish: " + current.state());
    }
}
