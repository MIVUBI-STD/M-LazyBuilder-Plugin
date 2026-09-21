package com.halokaryamedia.lazybuilder.performance;

import com.halokaryamedia.lazybuilder.performance.compatibility.RendererCompatibility;
import com.halokaryamedia.lazybuilder.performance.rendering.ChunkPipelineMetrics;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainArenaDrawDiagnostics;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainArenaDrawPlanner;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainDrawTransformStream;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainGpuResidencyLedger;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainGpuResidencyTracker;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainMultiDrawCapability;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainMultiDrawCommandStream;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainMultiDrawSubmissionBackend;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainPhysicalArenaManager;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainOwnershipProofTracker;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainRegionAllocationRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.util.Window;

/** Aggregates diagnostics only when requested; no background sampling is registered here. */
public final class PerformanceSnapshotReader {
    private PerformanceSnapshotReader() {
    }

    public static PerformanceSnapshot capture(MinecraftClient client, FrameMonitor frameMonitor) {
        ChunkPipelineMetrics.enableDetailedMetrics();
        StageTimingMetrics.enable();
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
        TerrainRegionAllocationRegistry.Snapshot arenas = TerrainGpuResidencyTracker.arenaSnapshot();
        TerrainArenaDrawPlanner.Plan drawPlan = TerrainArenaDrawDiagnostics.snapshot();
        TerrainPhysicalArenaManager.Snapshot physicalArenas = TerrainGpuResidencyTracker.physicalArenaSnapshot();
        TerrainOwnershipProofTracker.Snapshot ownershipProof = TerrainPhysicalArenaManager.ownershipProofSnapshot();
        TerrainDrawTransformStream.Snapshot transforms = TerrainDrawTransformStream.snapshot();
        TerrainMultiDrawCommandStream.Snapshot multiDraw = TerrainMultiDrawCommandStream.snapshot();
        TerrainMultiDrawCapability.Snapshot multiDrawCapability = TerrainMultiDrawCapability.current();
        TerrainMultiDrawSubmissionBackend.Snapshot multiDrawSubmission = TerrainMultiDrawSubmissionBackend.snapshot();
        RendererCompatibility.Snapshot renderer = RendererCompatibility.detect();

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
                arenas.plannedCapacityBytes(),
                arenas.allocatedBytes(),
                arenas.freeBytes(),
                arenas.fragmentedFreeBytes(),
                arenas.largestArenaBytes(),
                arenas.activeArenas(),
                arenas.activeAllocations(),
                arenas.allocationReuses(),
                arenas.reallocations(),
                arenas.compactions(),
                arenas.arenaGrowths(),
                arenas.allocationFailures(),
                drawPlan.eligibleCommands(),
                drawPlan.fallbackCommands(),
                drawPlan.arenaBatches(),
                drawPlan.potentialBindReductions(),
                drawPlan.baseVertexReadyCommands(),
                drawPlan.baseVertexBatches(),
                drawPlan.potentialBaseVertexBindReductions(),
                physicalArenas.residentBytes(),
                physicalArenas.activeArenas(),
                physicalArenas.residentBuffers(),
                physicalArenas.uploadedBytes(),
                physicalArenas.physicalDraws(),
                physicalArenas.customIndexDraws(),
                physicalArenas.bufferBinds(),
                physicalArenas.bindReuses(),
                physicalArenas.arenaResizes(),
                physicalArenas.invalidations(),
                physicalArenas.relocations(),
                physicalArenas.relocatedBytes(),
                physicalArenas.relocationFallbacks(),
                physicalArenas.exclusiveResidentBuffers(),
                physicalArenas.exclusiveRetiredBytes(),
                physicalArenas.exclusivePromotions(),
                physicalArenas.exclusiveRecoveries(),
                physicalArenas.exclusiveRecoveryFailures(),
                ownershipProof.observingBuffers(),
                ownershipProof.candidateBuffers(),
                ownershipProof.excludedCustomIndexBuffers(),
                ownershipProof.successfulProofDraws(),
                ownershipProof.proofResets(),
                ownershipProof.status(),
                transforms.commands(),
                transforms.physicalReadyCommands(),
                transforms.transformBlockedCommands(),
                transforms.multiDrawCandidateRuns(),
                transforms.potentialDrawCallReduction(),
                transforms.packedTransformBytes(),
                multiDraw.commands(),
                multiDraw.packedCommandBytes(),
                multiDraw.packedTransformBytes(),
                multiDrawCapability.status(),
                multiDrawSubmission.prepareAttempts(),
                multiDrawSubmission.preparedRuns(),
                multiDrawSubmission.submittedBatches(),
                multiDrawSubmission.submittedCommands(),
                multiDrawSubmission.reducedDrawCalls(),
                multiDrawSubmission.submissionFailures(),
                multiDrawSubmission.status(),
                renderer.ownerSummary(),
                chunkDebug,
                entityDebug,
                particleDebug
        );
    }
}
