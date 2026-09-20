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
                16384L,
                12288L,
                4096L,
                1024L,
                8192L,
                2,
                3,
                5L,
                7L,
                11L,
                13L,
                17L,
                37,
                5,
                12,
                25L,
                29,
                7,
                22L,
                65536L,
                4,
                18,
                131072L,
                900L,
                300L,
                250L,
                650L,
                3L,
                5L,
                6L,
                32768L,
                2L,
                8,
                49152L,
                9L,
                4L,
                1L,
                6,
                4,
                3,
                12000L,
                7L,
                "candidate",
                42,
                34,
                34,
                9,
                25L,
                504L,
                31,
                744L,
                496L,
                "model-offset-uniform",
                11L,
                7L,
                5L,
                23L,
                18L,
                1L,
                "active",
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
        assertEquals(16384L, snapshot.terrainArenaPlannedCapacityBytes());
        assertEquals(12288L, snapshot.terrainArenaAllocatedBytes());
        assertEquals(4096L, snapshot.terrainArenaFreeBytes());
        assertEquals(1024L, snapshot.terrainArenaFragmentedFreeBytes());
        assertEquals(8192L, snapshot.terrainArenaLargestBytes());
        assertEquals(2, snapshot.terrainArenaActiveArenas());
        assertEquals(3, snapshot.terrainArenaActiveAllocations());
        assertEquals(5L, snapshot.terrainArenaAllocationReuses());
        assertEquals(7L, snapshot.terrainArenaReallocations());
        assertEquals(11L, snapshot.terrainArenaCompactions());
        assertEquals(13L, snapshot.terrainArenaGrowths());
        assertEquals(17L, snapshot.terrainArenaAllocationFailures());
        assertEquals(37, snapshot.terrainArenaEligibleDrawCommands());
        assertEquals(5, snapshot.terrainArenaFallbackDrawCommands());
        assertEquals(12, snapshot.terrainArenaDrawBatches());
        assertEquals(25L, snapshot.potentialTerrainArenaBindReductions());
        assertEquals(29, snapshot.terrainArenaBaseVertexReadyDrawCommands());
        assertEquals(7, snapshot.terrainArenaBaseVertexDrawBatches());
        assertEquals(22L, snapshot.potentialTerrainArenaBaseVertexBindReductions());
        assertEquals(65536L, snapshot.terrainPhysicalArenaResidentBytes());
        assertEquals(4, snapshot.terrainPhysicalArenaCount());
        assertEquals(18, snapshot.terrainPhysicalResidentBuffers());
        assertEquals(131072L, snapshot.terrainPhysicalUploadedBytes());
        assertEquals(900L, snapshot.terrainPhysicalDraws());
        assertEquals(300L, snapshot.terrainPhysicalCustomIndexDraws());
        assertEquals(250L, snapshot.terrainPhysicalBufferBinds());
        assertEquals(650L, snapshot.terrainPhysicalBindReuses());
        assertEquals(3L, snapshot.terrainPhysicalArenaResizes());
        assertEquals(5L, snapshot.terrainPhysicalInvalidations());
        assertEquals(6L, snapshot.terrainPhysicalRelocations());
        assertEquals(32768L, snapshot.terrainPhysicalRelocatedBytes());
        assertEquals(2L, snapshot.terrainPhysicalRelocationFallbacks());
        assertEquals(8, snapshot.terrainExclusiveResidentBuffers());
        assertEquals(49152L, snapshot.terrainExclusiveRetiredBytes());
        assertEquals(9L, snapshot.terrainExclusivePromotions());
        assertEquals(4L, snapshot.terrainExclusiveRecoveries());
        assertEquals(1L, snapshot.terrainExclusiveRecoveryFailures());
        assertEquals(6, snapshot.terrainExclusiveOwnershipObservingBuffers());
        assertEquals(4, snapshot.terrainExclusiveOwnershipCandidateBuffers());
        assertEquals(3, snapshot.terrainExclusiveOwnershipExcludedBuffers());
        assertEquals(12000L, snapshot.terrainExclusiveOwnershipProofDraws());
        assertEquals(7L, snapshot.terrainExclusiveOwnershipProofResets());
        assertEquals("candidate", snapshot.terrainExclusiveOwnershipStatus());
        assertEquals(42, snapshot.terrainTransformStreamCommands());
        assertEquals(34, snapshot.terrainTransformPhysicalReadyCommands());
        assertEquals(34, snapshot.terrainMultiDrawTransformBlockedCommands());
        assertEquals(9, snapshot.terrainMultiDrawCandidateRuns());
        assertEquals(25L, snapshot.potentialTerrainMultiDrawDrawReductions());
        assertEquals(504L, snapshot.terrainTransformStreamBytes());
        assertEquals(31, snapshot.terrainMultiDrawPackedCommands());
        assertEquals(744L, snapshot.terrainMultiDrawPackedCommandBytes());
        assertEquals(496L, snapshot.terrainMultiDrawPackedTransformBytes());
        assertEquals("model-offset-uniform", snapshot.terrainMultiDrawCapability());
        assertEquals(11L, snapshot.terrainMultiDrawPrepareAttempts());
        assertEquals(7L, snapshot.terrainMultiDrawPreparedRuns());
        assertEquals(5L, snapshot.terrainMultiDrawSubmittedBatches());
        assertEquals(23L, snapshot.terrainMultiDrawSubmittedCommands());
        assertEquals(18L, snapshot.terrainMultiDrawReducedDrawCalls());
        assertEquals(1L, snapshot.terrainMultiDrawSubmissionFailures());
        assertEquals("active", snapshot.terrainMultiDrawSubmissionStatus());
        assertEquals("iris+sodium", snapshot.rendererPipelineOwner());
        assertEquals(120, snapshot.frameStats().fps());
        assertEquals(0.5D, snapshot.resourceStats().usedMemoryRatio());
        assertEquals(4, snapshot.chunkStats().tasksToBatch());
        assertEquals("iris+sodium", snapshot.compatibilityStats().rendererPipelineOwner());
    }
}
