package com.halokaryamedia.lazybuilder.builder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuilderRuntimeHistoryReplayMetricsTest {
    @Test
    void historyReplayCountersRemainSeparateFromMutationCounters() {
        BuilderRuntimeMetrics metrics = new BuilderRuntimeMetrics();
        metrics.recordHistoryUndoBlocks(10);
        metrics.recordHistoryRedoBlocks(7);
        metrics.recordHistoryUndoBlockEntityExtensions(3);
        metrics.recordHistoryUndoBiomeExtensions(4);
        metrics.recordHistoryRedoBlockEntityExtensions(5);
        metrics.recordHistoryRedoEntityExtensions(2);
        metrics.historyReplayFailure();

        var snapshot = metrics.snapshot();
        assertEquals(10, snapshot.historyUndoBlocks());
        assertEquals(7, snapshot.historyRedoBlocks());
        assertEquals(3, snapshot.historyUndoBlockEntityExtensions());
        assertEquals(4, snapshot.historyUndoBiomeExtensions());
        assertEquals(5, snapshot.historyRedoBlockEntityExtensions());
        assertEquals(2, snapshot.historyRedoEntityExtensions());
        assertEquals(1, snapshot.historyReplayFailures());
        assertEquals(0, snapshot.forwardBlocksDispatched());
        assertEquals(0, snapshot.rollbackBlocksDispatched());
    }
}
