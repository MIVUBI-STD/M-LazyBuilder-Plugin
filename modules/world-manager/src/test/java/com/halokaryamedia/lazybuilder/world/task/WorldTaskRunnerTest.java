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
                () -> runner.submit(WorldTaskType.CLONE, WorldId.create(), "Queued", progress -> "unused"));
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
