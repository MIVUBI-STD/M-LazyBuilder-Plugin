package com.halokaryamedia.lazybuilder.performance;

import com.halokaryamedia.lazybuilder.performance.compatibility.RendererCompatibility;
import com.halokaryamedia.lazybuilder.performance.rendering.ChunkPipelineMetrics;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainGpuResidencyLedger;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainGpuResidencyTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.util.Window;

/** Aggregates diagnostics only when requested; no background sampling is registered here. */
public final class PerformanceSnapshotReader {
    private PerformanceSnapshotReader() {
    }

    public static PerformanceSnapshot capture(MinecraftClient client, FrameMonitor frameMonitor) {
        int fps = Math.max(0, client.getCurrentFps());

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();

        int renderDistance = client.options.getViewDistance().getValue();
        int simulationDistance = client.options.getSimulationDistance().getValue();

        Window window = client.getWindow();
        boolean focused = client.isWindowFocused();
        boolean minimized = window != null && window.isMinimized();

        int completedChunkCount = client.worldRenderer == null ? 0 : client.worldRenderer.getCompletedChunkCount();
        String chunkDebug = client.worldRenderer == null ? "" : client.worldRenderer.getChunksDebugString();
        String entityDebug = client.worldRenderer == null ? "" : client.worldRenderer.getEntitiesDebugString();
        String particleDebug = client.particleManager == null ? "" : client.particleManager.getDebugString();

        ChunkBuilder chunkBuilder = client.worldRenderer == null ? null : client.worldRenderer.getChunkBuilder();
        int chunkTasksToBatch = chunkBuilder == null ? 0 : chunkBuilder.getToBatchCount();
        int chunksToUpload = chunkBuilder == null ? 0 : chunkBuilder.getChunksToUpload();
        int freeChunkBuffers = chunkBuilder == null ? 0 : chunkBuilder.getFreeBufferCount();
        TerrainGpuResidencyLedger.Snapshot residency = TerrainGpuResidencyTracker.snapshot();

        return new PerformanceSnapshot(
                fps,
                frameMonitor.currentFrameTimeMs(),
                frameMonitor.averageFrameTimeMs(),
                frameMonitor.worstRecentFrameTimeMs(),
                usedMemory,
                maxMemory,
                renderDistance,
                simulationDistance,
                focused,
                minimized,
                frameMonitor.pressure(),
                completedChunkCount,
                chunkTasksToBatch,
                chunksToUpload,
                freeChunkBuffers,
                ChunkPipelineMetrics.coalescedRebuildRequests(),
                ChunkPipelineMetrics.bufferAcquireMisses(),
                ChunkPipelineMetrics.avoidedUploadBufferBinds(),
                ChunkPipelineMetrics.storageSectionsRemapped(),
                ChunkPipelineMetrics.sectionVisibilityCacheHits(),
                ChunkPipelineMetrics.avoidedTranslucentSortTasks(),
                ChunkPipelineMetrics.avoidedTerrainSectionVisits(),
                ChunkPipelineMetrics.sectionBuilderBufferLookupHits(),
                ChunkPipelineMetrics.uploadBudgetStops(),
                residency.residentBytes(),
                residency.payloadBytes(),
                residency.headroomBytes(),
                residency.peakResidentBytes(),
                residency.residentBuffers(),
                residency.residentRegions(),
                residency.largestRegionBytes(),
                residency.largestRegionHeadroomBytes(),
                residency.regionRelocations(),
                ChunkPipelineMetrics.terrainGpuReclaimedBytes(),
                ChunkPipelineMetrics.terrainGpuReclaimedBuffers(),
                residency.projectedArenaBytes(),
                residency.projectedArenaSlackBytes(),
                residency.arenaCompactionCandidateRegions(),
                residency.potentialArenaReclaimBytes(),
                RendererCompatibility.detect().ownerSummary(),
                chunkDebug,
                entityDebug,
                particleDebug
        );
    }
}
