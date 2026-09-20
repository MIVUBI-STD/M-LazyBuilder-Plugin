package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ChunkPipelineMetricsTest {
    @BeforeEach
    void reset() {
        ChunkPipelineMetrics.resetForTest();
    }


    @Test
    void detailedCountersStayQuietUntilDiagnosticsAreEnabled() {
        ChunkPipelineMetrics.setDetailedMetricsEnabledForTest(false);
        assertFalse(ChunkPipelineMetrics.detailedMetricsEnabled());

        ChunkPipelineMetrics.recordSectionVisibilityCacheHit();
        ChunkPipelineMetrics.recordTerrainBufferLookupHit();
        ChunkPipelineMetrics.recordUploadBatch(4);

        assertEquals(0L, ChunkPipelineMetrics.sectionVisibilityCacheHits());
        assertEquals(0L, ChunkPipelineMetrics.terrainBufferLookupHits());
        assertEquals(0L, ChunkPipelineMetrics.avoidedUploadBufferBinds());

        ChunkPipelineMetrics.recordUploadBudgetStop();
        assertEquals(0L, ChunkPipelineMetrics.uploadBudgetStops());

        ChunkPipelineMetrics.enableDetailedMetrics();
        assertTrue(ChunkPipelineMetrics.detailedMetricsEnabled());
        ChunkPipelineMetrics.recordSectionVisibilityCacheHit();
        ChunkPipelineMetrics.recordTerrainBufferLookupHit();
        ChunkPipelineMetrics.recordUploadBatch(4);
        ChunkPipelineMetrics.recordUploadBudgetStop();

        assertEquals(1L, ChunkPipelineMetrics.sectionVisibilityCacheHits());
        assertEquals(1L, ChunkPipelineMetrics.terrainBufferLookupHits());
        assertEquals(3L, ChunkPipelineMetrics.avoidedUploadBufferBinds());
        assertEquals(1L, ChunkPipelineMetrics.uploadBudgetStops());
    }

    @Test
    void countsOnlyRecordedCoalescedRebuilds() {
        ChunkPipelineMetrics.recordCoalescedRebuild();
        ChunkPipelineMetrics.recordCoalescedRebuild();
        assertEquals(2L, ChunkPipelineMetrics.coalescedRebuildRequests());
    }

    @Test
    void countsChunkBufferAcquireMisses() {
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

    @Test
    void countsAvoidedTerrainSectionVisits() {
        ChunkPipelineMetrics.recordAvoidedTerrainSectionVisits(0L);
        ChunkPipelineMetrics.recordAvoidedTerrainSectionVisits(7L);
        ChunkPipelineMetrics.recordAvoidedTerrainSectionVisits(11L);
        assertEquals(18L, ChunkPipelineMetrics.avoidedTerrainSectionVisits());
    }

    @Test
    void countsSectionBuilderBufferLookupHits() {
        ChunkPipelineMetrics.recordSectionBuilderBufferLookupHit();
        ChunkPipelineMetrics.recordSectionBuilderBufferLookupHit();
        ChunkPipelineMetrics.recordSectionBuilderBufferLookupHit();
        ChunkPipelineMetrics.recordSectionBuilderBufferLookupHit();
        assertEquals(4L, ChunkPipelineMetrics.sectionBuilderBufferLookupHits());
    }

    @Test
    void countsUploadBudgetStops() {
        ChunkPipelineMetrics.recordUploadBudgetStop();
        ChunkPipelineMetrics.recordUploadBudgetStop();
        assertEquals(2L, ChunkPipelineMetrics.uploadBudgetStops());
    }

    @Test
    void countsTerrainGpuReclamationBytesAndBuffers() {
        ChunkPipelineMetrics.recordTerrainGpuReclamation(0L);
        ChunkPipelineMetrics.recordTerrainGpuReclamation(2L * 1024L * 1024L);
        ChunkPipelineMetrics.recordTerrainGpuReclamation(3L * 1024L * 1024L);
        assertEquals(5L * 1024L * 1024L, ChunkPipelineMetrics.terrainGpuReclaimedBytes());
        assertEquals(2L, ChunkPipelineMetrics.terrainGpuReclaimedBuffers());
    }
}
