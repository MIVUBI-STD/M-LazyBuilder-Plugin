package com.halokaryamedia.lazybuilder.builder.operation;

import java.time.Duration;
import java.util.Objects;

/**
 * Hard scheduling bounds for one Builder operation.
 *
 * <p>This record intentionally contains policy limits only; it does not own a clock
 * or a scheduler. Runtime executors consume the budget and decide when to yield.</p>
 */
public record ExecutionBudget(
        Duration maxSliceDuration,
        int maxChunksInFlight,
        long maxPendingMutations,
        long maxWorkingMemoryBytes
) {
    public ExecutionBudget {
        Objects.requireNonNull(maxSliceDuration, "maxSliceDuration");
        if (maxSliceDuration.isZero() || maxSliceDuration.isNegative()) {
            throw new IllegalArgumentException("maxSliceDuration must be > 0");
        }
        if (maxChunksInFlight <= 0) {
            throw new IllegalArgumentException("maxChunksInFlight must be > 0");
        }
        if (maxPendingMutations <= 0) {
            throw new IllegalArgumentException("maxPendingMutations must be > 0");
        }
        if (maxWorkingMemoryBytes <= 0) {
            throw new IllegalArgumentException("maxWorkingMemoryBytes must be > 0");
        }
    }
}
