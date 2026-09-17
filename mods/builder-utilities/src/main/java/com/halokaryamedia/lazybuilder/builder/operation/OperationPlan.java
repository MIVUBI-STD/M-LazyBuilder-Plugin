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
