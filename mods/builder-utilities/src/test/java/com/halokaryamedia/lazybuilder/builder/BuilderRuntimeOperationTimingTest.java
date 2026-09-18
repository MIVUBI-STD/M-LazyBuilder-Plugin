package com.halokaryamedia.lazybuilder.builder;

import com.halokaryamedia.lazybuilder.builder.operation.OperationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuilderRuntimeOperationTimingTest {
    @Test
    void recordsPerOperationIdentityAndTiming() {
        BuilderRuntimeMetrics metrics = new BuilderRuntimeMetrics();
        metrics.operationStarted();
        metrics.terminal(
                OperationState.COMPLETED,
                12_345L,
                "world@operation-1",
                1_000_000L,
                42L);

        var s = metrics.snapshot();
        assertEquals(12_345L, s.lastOperationNanos());
        assertEquals(12_345L, s.totalOperationNanos());
        assertEquals(12_345L, s.maxOperationNanos());
        assertEquals("world@operation-1", s.lastOperationId());
        assertEquals(1_000_000L, s.lastOperationPlannedBlocks());
        assertEquals(42L, s.lastOperationPlannedExtensions());
    }
}
