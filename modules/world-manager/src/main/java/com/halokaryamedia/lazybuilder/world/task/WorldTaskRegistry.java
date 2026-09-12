package com.halokaryamedia.lazybuilder.world.task;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory owner for desktop-visible asynchronous world task state.
 *
 * <p>This registry intentionally owns no worker threads. Services/task runners publish state here;
 * the control bridge only reads snapshots. History is bounded so a long-running server cannot grow
 * memory without limit.</p>
 */
public final class WorldTaskRegistry {
    public static final int DEFAULT_HISTORY_LIMIT = 256;

    private final Clock clock;
    private final int historyLimit;
    private final LinkedHashMap<UUID, WorldTaskSnapshot> tasks = new LinkedHashMap<>();

    public WorldTaskRegistry() {
        this(Clock.systemUTC(), DEFAULT_HISTORY_LIMIT);
    }

    public WorldTaskRegistry(Clock clock, int historyLimit) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (historyLimit < 1) {
            throw new IllegalArgumentException("historyLimit must be at least 1");
        }
        this.historyLimit = historyLimit;
    }

    public synchronized WorldTaskSnapshot create(WorldTaskType type, WorldId worldId, String message) {
        Objects.requireNonNull(type, "type");
        Instant now = clock.instant();
        WorldTaskSnapshot snapshot = new WorldTaskSnapshot(
                UUID.randomUUID(), type, worldId, WorldTaskState.QUEUED, 0,
                message, "", "", now, now
        );
        tasks.put(snapshot.taskId(), snapshot);
        trimHistory();
        return snapshot;
    }

    public synchronized WorldTaskSnapshot markRunning(UUID taskId, String message) {
        WorldTaskSnapshot current = require(taskId);
        requireTransition(current.state(), WorldTaskState.RUNNING);
        return replace(current, WorldTaskState.RUNNING, current.progressPercent(), message, "", "");
    }

    public synchronized WorldTaskSnapshot updateProgress(UUID taskId, int progressPercent, String message) {
        WorldTaskSnapshot current = require(taskId);
        if (current.state() != WorldTaskState.RUNNING) {
            throw new IllegalStateException("Task progress can only be updated while RUNNING");
        }
        if (progressPercent < current.progressPercent()) {
            throw new IllegalArgumentException("Task progress must not move backwards");
        }
        return replace(current, current.state(), progressPercent, message, current.result(), current.error());
    }

    public synchronized WorldTaskSnapshot succeed(UUID taskId, String result, String message) {
        WorldTaskSnapshot current = require(taskId);
        requireTransition(current.state(), WorldTaskState.SUCCEEDED);
        return replace(current, WorldTaskState.SUCCEEDED, 100, message, result, "");
    }

    public synchronized WorldTaskSnapshot fail(UUID taskId, String error, String message) {
        WorldTaskSnapshot current = require(taskId);
        requireTransition(current.state(), WorldTaskState.FAILED);
        String safeError = error == null ? "" : error.trim();
        if (safeError.isEmpty()) {
            throw new IllegalArgumentException("error must not be blank");
        }
        return replace(current, WorldTaskState.FAILED, current.progressPercent(), message, "", safeError);
    }

    public synchronized Optional<WorldTaskSnapshot> find(UUID taskId) {
        return Optional.ofNullable(tasks.get(Objects.requireNonNull(taskId, "taskId")));
    }

    public synchronized List<WorldTaskSnapshot> recent() {
        List<WorldTaskSnapshot> snapshots = new ArrayList<>(tasks.values());
        java.util.Collections.reverse(snapshots);
        return List.copyOf(snapshots);
    }

    private WorldTaskSnapshot require(UUID taskId) {
        WorldTaskSnapshot snapshot = tasks.get(Objects.requireNonNull(taskId, "taskId"));
        if (snapshot == null) {
            throw new IllegalArgumentException("Unknown world task: " + taskId);
        }
        return snapshot;
    }

    private WorldTaskSnapshot replace(
            WorldTaskSnapshot current,
            WorldTaskState state,
            int progressPercent,
            String message,
            String result,
            String error
    ) {
        WorldTaskSnapshot updated = new WorldTaskSnapshot(
                current.taskId(), current.type(), current.worldId(), state, progressPercent,
                message, result, error, current.createdAt(), clock.instant()
        );
        tasks.put(updated.taskId(), updated);
        return updated;
    }

    private static void requireTransition(WorldTaskState current, WorldTaskState target) {
        boolean allowed = switch (target) {
            case RUNNING -> current == WorldTaskState.QUEUED;
            case SUCCEEDED, FAILED -> current == WorldTaskState.RUNNING;
            case QUEUED -> false;
        };
        if (!allowed) {
            throw new IllegalStateException("Invalid world task transition " + current + " -> " + target);
        }
    }

    private void trimHistory() {
        while (tasks.size() > historyLimit) {
            UUID removable = null;
            for (Map.Entry<UUID, WorldTaskSnapshot> entry : tasks.entrySet()) {
                WorldTaskState state = entry.getValue().state();
                if (state == WorldTaskState.SUCCEEDED || state == WorldTaskState.FAILED) {
                    removable = entry.getKey();
                    break;
                }
            }
            if (removable == null) {
                return;
            }
            tasks.remove(removable);
        }
    }
}
