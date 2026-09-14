package com.halokaryamedia.lazybuilder.world.task;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable desktop-facing snapshot for one asynchronous world task. */
public record WorldTaskSnapshot(
        UUID taskId,
        WorldTaskType type,
        WorldId worldId,
        WorldTaskState state,
        int progressPercent,
        String message,
        String result,
        String error,
        Instant createdAt,
        Instant updatedAt
) {
    public WorldTaskSnapshot {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (progressPercent < 0 || progressPercent > 100) {
            throw new IllegalArgumentException("progressPercent must be in range 0..100");
        }
        message = message == null ? "" : message;
        result = result == null ? "" : result;
        error = error == null ? "" : error;
    }
}
