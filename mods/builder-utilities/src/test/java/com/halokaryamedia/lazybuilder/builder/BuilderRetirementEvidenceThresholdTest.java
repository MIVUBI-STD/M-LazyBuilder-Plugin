package com.halokaryamedia.lazybuilder.builder;

import com.halokaryamedia.lazybuilder.builder.operation.OperationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuilderRetirementEvidenceThresholdTest {
    @Test
    void completedOperationTracksLargestObservedPlannedWork() {
        BuilderRuntimeMetrics metrics = new BuilderRuntimeMetrics();
        metrics.terminal(OperationState.COMPLETED, 10, "small", 10_000, 0);
        metrics.terminal(OperationState.COMPLETED, 20, "large", 1_500_000, 32);
        metrics.terminal(OperationState.COMPLETED, 30, "later-small", 5_000, 0);

        var snapshot = metrics.snapshot();
        assertEquals(1_500_000, snapshot.maxCompletedPlannedBlocks());
        assertEquals(32, snapshot.maxCompletedPlannedExtensions());
    }
}
