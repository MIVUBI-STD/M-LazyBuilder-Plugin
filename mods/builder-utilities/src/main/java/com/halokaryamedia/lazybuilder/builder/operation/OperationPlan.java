package com.halokaryamedia.lazybuilder.builder.operation;

import com.halokaryamedia.lazybuilder.builder.region.ChunkWorkUnit;

import java.util.List;
import java.util.Objects;

/**
 * Immutable chunk-local execution plan produced before any world mutation starts.
 */
public record OperationPlan(
        BuilderOperation operation,
        List<ChunkWorkUnit> workUnits,
        long candidateBlockCount
) {
    public OperationPlan {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(operation.id(), "operation.id()");
        if (operation.type() == null || operation.type().isBlank()) {
            throw new IllegalArgumentException("operation.type() must not be blank");
        }
        Objects.requireNonNull(operation.region(), "operation.region()");
        Objects.requireNonNull(operation.seed(), "operation.seed()");
        Objects.requireNonNull(operation.executionBudget(), "operation.executionBudget()");
        Objects.requireNonNull(operation.cancellationToken(), "operation.cancellationToken()");
        Objects.requireNonNull(operation.readMode(), "operation.readMode()");
        Objects.requireNonNull(operation.historyRequirement(), "operation.historyRequirement()");
        Objects.requireNonNull(operation.cancellationDisposition(), "operation.cancellationDisposition()");

        Objects.requireNonNull(workUnits, "workUnits");
        workUnits = List.copyOf(workUnits);
        if (workUnits.isEmpty()) {
            throw new IllegalArgumentException("workUnits must not be empty");
        }

        long computed = 0L;
        for (ChunkWorkUnit unit : workUnits) {
            computed = Math.addExact(computed, unit.candidateBlockCount());
        }
        if (candidateBlockCount != computed) {
            throw new IllegalArgumentException(
                    "candidateBlockCount does not match work units: expected " + computed + ", got " + candidateBlockCount);
        }
    }
}
