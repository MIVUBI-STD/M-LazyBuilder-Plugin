package com.halokaryamedia.lazybuilder.builder;

import com.halokaryamedia.lazybuilder.builder.operation.OperationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuilderRuntimeMetricsExtensionTest {
    @Test
    void extensionCountersAccumulateWithoutAffectingTerminalCounts() {
        BuilderRuntimeMetrics metrics = new BuilderRuntimeMetrics();
        metrics.operationStarted();
        metrics.recordForwardBlockEntityExtensions(2);
        metrics.recordForwardBiomeExtensions(4);
        metrics.recordForwardEntityExtensions(3);
        metrics.recordRollbackBlockEntityExtensions(1);
        metrics.recordRollbackEntityExtensions(1);
        metrics.extensionConflict();
        metrics.extensionFailure();
        metrics.terminal(OperationState.FAILED);

        var snapshot = metrics.snapshot();
        assertEquals(2, snapshot.forwardBlockEntityExtensions());
        assertEquals(4, snapshot.forwardBiomeExtensions());
        assertEquals(3, snapshot.forwardEntityExtensions());
        assertEquals(1, snapshot.rollbackBlockEntityExtensions());
        assertEquals(1, snapshot.rollbackEntityExtensions());
        assertEquals(1, snapshot.extensionConflicts());
        assertEquals(1, snapshot.extensionFailures());
        assertEquals(1, snapshot.operationsFailed());
        assertEquals(1, snapshot.conflicts());
    }
}
