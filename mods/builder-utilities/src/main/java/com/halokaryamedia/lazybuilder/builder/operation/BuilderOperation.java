package com.halokaryamedia.lazybuilder.builder.operation;

import com.halokaryamedia.lazybuilder.builder.region.BuilderRegion;

import java.util.UUID;

/**
 * Minimal immutable-facing contract for every future Builder world mutation.
 *
 * <p>Tool-specific payloads belong in implementations. Scheduling, cancellation,
 * deterministic procedural choices and spatial planning are shared here.</p>
 */
public interface BuilderOperation {
    UUID id();

    String type();

    BuilderRegion region();

    OperationSeed seed();

    ExecutionBudget executionBudget();

    CancellationToken cancellationToken();
}
