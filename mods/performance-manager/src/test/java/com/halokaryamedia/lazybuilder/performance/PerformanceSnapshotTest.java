package com.halokaryamedia.lazybuilder.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PerformanceSnapshotTest {
    @Test
    void memoryRatioRemainsBoundedWithChunkDiagnosticsPresent() {
        PerformanceSnapshot snapshot = new PerformanceSnapshot(
                120,
                8.0D,
                9.0D,
                14.0D,
                512L,
                1024L,
                16,
                12,
                true,
                false,
                FramePressure.NORMAL,
                200,
                4,
                2,
                3,
                7L,
                5L,
                11L,
                13L,
                17L,
                19L,
                23L,
                29L,
                31L,
                4096L,
                3072L,
                1024L,
                8192L,
                12,
                3,
                2048L,
                512L,
                5L,
                6144L,
                2L,
                12288L,
                4096L,
                2,
                7168L,
                "iris+sodium",
                "chunks",
                "entities",
                "particles"
        );

        assertEquals(0.5D, snapshot.usedMemoryRatio());
        assertEquals(4, snapshot.chunkTasksToBatch());
        assertEquals(2, snapshot.chunksToUpload());
        assertEquals(3, snapshot.freeChunkBuffers());
        assertEquals(7L, snapshot.coalescedChunkRebuildRequests());
        assertEquals(5L, snapshot.chunkBufferAcquireMisses());
        assertEquals(11L, snapshot.avoidedChunkUploadBufferBinds());
        assertEquals(13L, snapshot.remappedChunkStorageSections());
        assertEquals(17L, snapshot.sectionVisibilityCacheHits());
        assertEquals(19L, snapshot.avoidedTranslucentSortTasks());
        assertEquals(23L, snapshot.avoidedTerrainSectionVisits());
        assertEquals(29L, snapshot.sectionBuilderBufferLookupHits());
        assertEquals(31L, snapshot.chunkUploadBudgetStops());
        assertEquals(4096L, snapshot.terrainGpuResidentBytes());
        assertEquals(3072L, snapshot.terrainGpuPayloadBytes());
        assertEquals(1024L, snapshot.terrainGpuHeadroomBytes());
        assertEquals(8192L, snapshot.peakTerrainGpuResidentBytes());
        assertEquals(12, snapshot.terrainGpuResidentBuffers());
        assertEquals(3, snapshot.terrainGpuResidentRegions());
        assertEquals(2048L, snapshot.largestTerrainRegionBytes());
        assertEquals(512L, snapshot.largestTerrainRegionHeadroomBytes());
        assertEquals(5L, snapshot.terrainBufferRegionRelocations());
        assertEquals(6144L, snapshot.terrainGpuReclaimedBytes());
        assertEquals(2L, snapshot.terrainGpuReclaimedBuffers());
        assertEquals(12288L, snapshot.projectedTerrainArenaBytes());
        assertEquals(4096L, snapshot.projectedTerrainArenaSlackBytes());
        assertEquals(2, snapshot.terrainArenaCompactionCandidateRegions());
        assertEquals(7168L, snapshot.potentialTerrainArenaReclaimBytes());
        assertEquals("iris+sodium", snapshot.rendererPipelineOwner());
    }
}
