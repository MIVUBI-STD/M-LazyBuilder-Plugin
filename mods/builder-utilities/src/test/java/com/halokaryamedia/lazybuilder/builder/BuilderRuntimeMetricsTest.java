package com.halokaryamedia.lazybuilder.builder;

import com.halokaryamedia.lazybuilder.builder.operation.OperationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuilderRuntimeMetricsTest {
    @Test
    void aggregatesProofCountersAndMaxSlice() {
        BuilderRuntimeMetrics metrics = new BuilderRuntimeMetrics();
        metrics.operationStarted();
        metrics.recordForwardSlice(2, 100, 5_000_000);
        metrics.recordForwardSlice(1, 20, 3_000_000);
        metrics.recordRollbackSlice(1, 10, 7_000_000);
        metrics.conflict();
        metrics.budgetExceeded();
        metrics.forwardDispatchYielded();
        metrics.forwardDispatchYielded();
        metrics.rollbackDispatchYielded();
        metrics.terminal(OperationState.CANCELLED);

        var snapshot = metrics.snapshot();
        assertEquals(1, snapshot.operationsStarted());
        assertEquals(1, snapshot.operationsCancelled());
        assertEquals(3, snapshot.forwardChunksVisited());
        assertEquals(120, snapshot.forwardBlocksDispatched());
        assertEquals(10, snapshot.rollbackBlocksDispatched());
        assertEquals(2, snapshot.forwardDispatchYields());
        assertEquals(1, snapshot.rollbackDispatchYields());
        assertEquals(7_000_000, snapshot.maxSliceNanos());
        assertEquals("CANCELLED", snapshot.lastOutcome());
    }
}
