package com.halokaryamedia.lazybuilder.builder.operation;

import com.halokaryamedia.lazybuilder.builder.region.ChunkWorkUnit;

import java.util.List;
import java.util.Objects;

/**
 * Owner-thread queue that dispatches work in plan order and stops at cooperative
 * cancellation boundaries. Parallel executors may consume each returned batch,
 * but queue mutation itself is intentionally single-owner.
 */
public final class DeterministicWorkQueue {
    private final OperationPlan plan;
    private int nextIndex;

    public DeterministicWorkQueue(OperationPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    public List<ChunkWorkUnit> pollBatch() {
        if (isExhausted() || plan.operation().cancellationToken().isCancellationRequested()) {
            return List.of();
        }

        int remaining = plan.workUnits().size() - nextIndex;
        int count = Math.min(remaining, plan.operation().executionBudget().maxChunksInFlight());
        int endExclusive = nextIndex + count;
        List<ChunkWorkUnit> batch = List.copyOf(plan.workUnits().subList(nextIndex, endExclusive));
        nextIndex = endExclusive;
        return batch;
    }

    public boolean isExhausted() {
        return nextIndex >= plan.workUnits().size();
    }

    public int remainingWorkUnits() {
        return plan.workUnits().size() - nextIndex;
    }
}
