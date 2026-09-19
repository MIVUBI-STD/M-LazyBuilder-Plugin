package com.halokaryamedia.lazybuilder.world.task;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldTaskRegistryTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-12T18:00:00Z"), ZoneOffset.UTC);

    @Test
    void tracksQueuedRunningProgressAndSuccess() {
        WorldTaskRegistry registry = new WorldTaskRegistry(CLOCK, 8);
        WorldId worldId = WorldId.create();

        WorldTaskSnapshot queued = registry.create(WorldTaskType.EXPORT, worldId, "Queued");
        assertEquals(WorldTaskState.QUEUED, queued.state());
        assertEquals(0, queued.progressPercent());

        WorldTaskSnapshot running = registry.markRunning(queued.taskId(), "Exporting");
        assertEquals(WorldTaskState.RUNNING, running.state());

        WorldTaskSnapshot progress = registry.updateProgress(queued.taskId(), 40, "Writing archive");
        assertEquals(40, progress.progressPercent());

        WorldTaskSnapshot success = registry.succeed(queued.taskId(), "exports/world.zip", "Complete");
        assertEquals(WorldTaskState.SUCCEEDED, success.state());
        assertEquals(100, success.progressPercent());
        assertEquals("exports/world.zip", success.result());
    }

    @Test
    void rejectsInvalidTransitionsAndBackwardProgress() {
        WorldTaskRegistry registry = new WorldTaskRegistry(CLOCK, 8);
        WorldTaskSnapshot task = registry.create(WorldTaskType.DUPLICATE, WorldId.create(), "Queued");

        assertThrows(IllegalStateException.class, () -> registry.succeed(task.taskId(), "", "Done"));
        registry.markRunning(task.taskId(), "Running");
        registry.updateProgress(task.taskId(), 60, "More than half");
        assertThrows(IllegalArgumentException.class, () -> registry.updateProgress(task.taskId(), 59, "Backwards"));
    }

    @Test
    void boundsStoredTaskTextWithoutLosingTerminalState() {
        WorldTaskRegistry registry = new WorldTaskRegistry(CLOCK, 8);
        WorldTaskSnapshot task = registry.create(
                WorldTaskType.EXPORT,
                WorldId.create(),
                "m".repeat(WorldTaskRegistry.MAX_MESSAGE_CHARS + 500)
        );
        assertEquals(WorldTaskRegistry.MAX_MESSAGE_CHARS, task.message().length());
        assertTrue(task.message().endsWith("[truncated]"));

        registry.markRunning(task.taskId(), "running");
        WorldTaskSnapshot success = registry.succeed(
                task.taskId(),
                "r".repeat(WorldTaskRegistry.MAX_RESULT_CHARS + 500),
                "done"
        );
        assertEquals(WorldTaskRegistry.MAX_RESULT_CHARS, success.result().length());
        assertTrue(success.result().endsWith("[truncated]"));

        WorldTaskSnapshot failed = registry.create(
                WorldTaskType.BACKUP,
                WorldId.create(),
                "queued"
        );
        registry.markRunning(failed.taskId(), "running");
        WorldTaskSnapshot terminal = registry.fail(
                failed.taskId(),
                "e".repeat(WorldTaskRegistry.MAX_ERROR_CHARS + 500),
                "failed"
        );
        assertEquals(WorldTaskRegistry.MAX_ERROR_CHARS, terminal.error().length());
        assertTrue(terminal.error().endsWith("[truncated]"));
    }

    @Test
    void rejectsBlankRequiredTaskTextAtOwnerBoundary() {
        WorldTaskRegistry registry = new WorldTaskRegistry(CLOCK, 8);
        assertThrows(IllegalArgumentException.class,
                () -> registry.create(WorldTaskType.EXPORT, WorldId.create(), "   "));

        WorldTaskSnapshot task = registry.create(WorldTaskType.EXPORT, WorldId.create(), "queued");
        registry.markRunning(task.taskId(), "running");
        assertThrows(IllegalArgumentException.class,
                () -> registry.fail(task.taskId(), "   ", "failed"));
    }

    @Test
    void terminalTransitionTrimsOverflowWithoutWaitingForAnotherTask() {
        WorldTaskRegistry registry = new WorldTaskRegistry(CLOCK, 2);
        WorldTaskSnapshot first = registry.create(WorldTaskType.BACKUP, WorldId.create(), "first");
        WorldTaskSnapshot second = registry.create(WorldTaskType.EXPORT, WorldId.create(), "second");
        WorldTaskSnapshot third = registry.create(WorldTaskType.DUPLICATE, WorldId.create(), "third");

        assertEquals(3, registry.recent().size(), "active tasks may temporarily exceed history limit");

        registry.markRunning(first.taskId(), "running");
        registry.succeed(first.taskId(), "done", "done");

        assertEquals(2, registry.recent().size(),
                "terminal transition must immediately retire oldest terminal overflow");
        assertTrue(registry.find(first.taskId()).isEmpty());
        assertTrue(registry.find(second.taskId()).isPresent());
        assertTrue(registry.find(third.taskId()).isPresent());
    }

    @Test
    void keepsActiveTasksWhenTrimmingCompletedHistory() {
        WorldTaskRegistry registry = new WorldTaskRegistry(CLOCK, 2);
        WorldTaskSnapshot active = registry.create(WorldTaskType.IMPORT, null, "Waiting for import");

        WorldTaskSnapshot first = registry.create(WorldTaskType.EXPORT, WorldId.create(), "Queued");
        registry.markRunning(first.taskId(), "Running");
        registry.succeed(first.taskId(), "first.zip", "Done");

        WorldTaskSnapshot second = registry.create(WorldTaskType.BACKUP, WorldId.create(), "Queued");
        registry.markRunning(second.taskId(), "Running");
        registry.succeed(second.taskId(), "backup.zip", "Done");

        assertTrue(registry.find(active.taskId()).isPresent());
        assertEquals(2, registry.recent().size());
    }
}
