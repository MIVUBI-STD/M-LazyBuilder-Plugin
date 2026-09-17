package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ChunkPipelineMetricsTest {
    @BeforeEach
    void reset() {
        ChunkPipelineMetrics.resetForTest();
    }

    @Test
    void countsOnlyRecordedCoalescedRebuilds() {
        assertEquals(0L, ChunkPipelineMetrics.coalescedRebuildRequests());

        ChunkPipelineMetrics.recordCoalescedRebuild();
        ChunkPipelineMetrics.recordCoalescedRebuild();

        assertEquals(2L, ChunkPipelineMetrics.coalescedRebuildRequests());
    }
}
