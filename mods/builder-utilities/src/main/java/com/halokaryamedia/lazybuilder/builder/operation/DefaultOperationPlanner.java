package com.halokaryamedia.lazybuilder.builder.operation;

import com.halokaryamedia.lazybuilder.builder.region.ChunkWorkUnit;
import com.halokaryamedia.lazybuilder.builder.region.RegionPlanner;

import java.util.List;
import java.util.Objects;

/**
 * Converts an operation region into deterministic chunk-local work before execution.
 */
public final class DefaultOperationPlanner implements OperationPlanner {
    private final RegionPlanner regionPlanner;

    public DefaultOperationPlanner(RegionPlanner regionPlanner) {
        this.regionPlanner = Objects.requireNonNull(regionPlanner, "regionPlanner");
    }

    @Override
    public OperationPlan plan(BuilderOperation operation) {
        Objects.requireNonNull(operation, "operation");
        operation.cancellationToken().throwIfCancellationRequested();

        List<ChunkWorkUnit> work = regionPlanner.plan(operation.region());
        long candidateBlocks = 0L;
        for (ChunkWorkUnit unit : work) {
            candidateBlocks = Math.addExact(candidateBlocks, unit.candidateBlockCount());
        }
        return new OperationPlan(operation, work, candidateBlocks);
    }
}
