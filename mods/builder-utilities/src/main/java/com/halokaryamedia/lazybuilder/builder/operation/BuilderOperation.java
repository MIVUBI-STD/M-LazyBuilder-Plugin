package com.halokaryamedia.lazybuilder.builder.operation;

import com.halokaryamedia.lazybuilder.builder.history.HistoryRequirement;
import com.halokaryamedia.lazybuilder.builder.region.BuilderRegion;

import java.util.UUID;

/**
 * Minimal immutable-facing contract for every future Builder world mutation.
 *
 * <p>Tool-specific payloads belong in implementations. Scheduling, cancellation,
 * deterministic procedural choices, spatial planning and safety semantics are
 * explicit here so tools cannot silently invent incompatible execution rules.</p>
 */
public interface BuilderOperation {
    UUID id();

    String type();

    BuilderRegion region();

    OperationSeed seed();

    ExecutionBudget executionBudget();

    CancellationToken cancellationToken();

    MutationReadMode readMode();

    HistoryRequirement historyRequirement();

    CancellationDisposition cancellationDisposition();
}
