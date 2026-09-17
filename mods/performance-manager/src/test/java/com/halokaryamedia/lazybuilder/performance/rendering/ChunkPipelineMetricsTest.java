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

    @Test
    void countsChunkBufferAcquireMisses() {
        assertEquals(0L, ChunkPipelineMetrics.bufferAcquireMisses());
        ChunkPipelineMetrics.recordBufferAcquireMiss();
        ChunkPipelineMetrics.recordBufferAcquireMiss();
        ChunkPipelineMetrics.recordBufferAcquireMiss();
        assertEquals(3L, ChunkPipelineMetrics.bufferAcquireMisses());
    }

    @Test
    void countsOnlyBufferBindsActuallyAvoidedByBatching() {
        ChunkPipelineMetrics.recordUploadBatch(1);
        ChunkPipelineMetrics.recordUploadBatch(4);
        assertEquals(3L, ChunkPipelineMetrics.avoidedUploadBufferBinds());
    }

    @Test
    void countsOnlyPositiveStorageRemaps() {
        ChunkPipelineMetrics.recordStorageSectionsRemapped(0);
        ChunkPipelineMetrics.recordStorageSectionsRemapped(12);
        assertEquals(12L, ChunkPipelineMetrics.storageSectionsRemapped());
    }

    @Test
    void countsSectionVisibilityCacheHits() {
        ChunkPipelineMetrics.recordSectionVisibilityCacheHit();
        ChunkPipelineMetrics.recordSectionVisibilityCacheHit();
        assertEquals(2L, ChunkPipelineMetrics.sectionVisibilityCacheHits());
    }

    @Test
    void countsAvoidedTranslucentSortTasks() {
        ChunkPipelineMetrics.recordAvoidedTranslucentSortTask();
        ChunkPipelineMetrics.recordAvoidedTranslucentSortTask();
        ChunkPipelineMetrics.recordAvoidedTranslucentSortTask();
        assertEquals(3L, ChunkPipelineMetrics.avoidedTranslucentSortTasks());
    }
}
