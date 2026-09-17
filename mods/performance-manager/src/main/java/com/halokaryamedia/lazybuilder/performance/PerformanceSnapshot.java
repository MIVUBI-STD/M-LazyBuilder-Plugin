package com.halokaryamedia.lazybuilder.performance;

/** On-demand diagnostic snapshot of current LazyBuilder client performance. */
public record PerformanceSnapshot(
        int fps,
        double currentFrameTimeMs,
        double averageFrameTimeMs,
        double worstRecentFrameTimeMs,
        long usedMemoryBytes,
        long maxMemoryBytes,
        int renderDistance,
        int simulationDistance,
        boolean windowFocused,
        boolean windowMinimized,
        FramePressure pressure,
        int completedChunkCount,
        int chunkTasksToBatch,
        int chunksToUpload,
        int freeChunkBuffers,
        long coalescedChunkRebuildRequests,
        long chunkBufferAcquireMisses,
        long avoidedChunkUploadBufferBinds,
        long remappedChunkStorageSections,
        long sectionVisibilityCacheHits,
        long avoidedTranslucentSortTasks,
        long avoidedTerrainSectionVisits,
        long sectionBuilderBufferLookupHits,
        long chunkUploadBudgetStops,
        long terrainGpuResidentBytes,
        long terrainGpuPayloadBytes,
        long terrainGpuHeadroomBytes,
        long peakTerrainGpuResidentBytes,
        int terrainGpuResidentBuffers,
        int terrainGpuResidentRegions,
        long largestTerrainRegionBytes,
        long largestTerrainRegionHeadroomBytes,
        long terrainBufferRegionRelocations,
        long terrainGpuReclaimedBytes,
        long terrainGpuReclaimedBuffers,
        long projectedTerrainArenaBytes,
        long projectedTerrainArenaSlackBytes,
        int terrainArenaCompactionCandidateRegions,
        long potentialTerrainArenaReclaimBytes,
        long terrainArenaPlannedCapacityBytes,
        long terrainArenaAllocatedBytes,
        long terrainArenaFreeBytes,
        long terrainArenaFragmentedFreeBytes,
        long terrainArenaLargestBytes,
        int terrainArenaActiveArenas,
        int terrainArenaActiveAllocations,
        long terrainArenaAllocationReuses,
        long terrainArenaReallocations,
        long terrainArenaCompactions,
        long terrainArenaGrowths,
        long terrainArenaAllocationFailures,
        String rendererPipelineOwner,
        String chunkDebug,
        String entityDebug,
        String particleDebug
) {
    public PerformanceSnapshot {
        rendererPipelineOwner = rendererPipelineOwner == null ? "" : rendererPipelineOwner;
        chunkDebug = chunkDebug == null ? "" : chunkDebug;
        entityDebug = entityDebug == null ? "" : entityDebug;
        particleDebug = particleDebug == null ? "" : particleDebug;
    }

    public double usedMemoryRatio() {
        if (maxMemoryBytes <= 0L) return 0.0D;
        return Math.min(1.0D, (double) usedMemoryBytes / (double) maxMemoryBytes);
    }
}
