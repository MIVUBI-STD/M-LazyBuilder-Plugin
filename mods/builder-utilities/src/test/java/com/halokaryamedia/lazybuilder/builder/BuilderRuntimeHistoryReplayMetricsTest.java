package com.halokaryamedia.lazybuilder.builder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuilderRuntimeHistoryReplayMetricsTest {
    @Test
    void historyReplayCountersRemainSeparateFromMutationCounters() {
        BuilderRuntimeMetrics metrics = new BuilderRuntimeMetrics();
        metrics.recordHistoryUndoBlocks(10);
        metrics.recordHistoryRedoBlocks(7);
        metrics.recordHistoryUndoBiomeExtensions(4);
        metrics.recordHistoryRedoEntityExtensions(2);
        metrics.historyReplayFailure();

        var snapshot = metrics.snapshot();
        assertEquals(10, snapshot.historyUndoBlocks());
        assertEquals(7, snapshot.historyRedoBlocks());
        assertEquals(4, snapshot.historyUndoBiomeExtensions());
        assertEquals(2, snapshot.historyRedoEntityExtensions());
        assertEquals(1, snapshot.historyReplayFailures());
        assertEquals(0, snapshot.forwardBlocksDispatched());
        assertEquals(0, snapshot.rollbackBlocksDispatched());
    }
}
