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
