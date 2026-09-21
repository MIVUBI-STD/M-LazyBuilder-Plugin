package com.halokaryamedia.lazybuilder.performance;

import com.halokaryamedia.lazybuilder.performance.compatibility.FirstPartyRendererReadiness;
import com.halokaryamedia.lazybuilder.performance.compatibility.OptimizationCompatibility;
import com.halokaryamedia.lazybuilder.performance.compatibility.RendererCompatibility;
import com.halokaryamedia.lazybuilder.performance.rendering.ChunkPipelineMetrics;
import com.halokaryamedia.lazybuilder.performance.rendering.GpuCapabilityProfile;
import com.halokaryamedia.lazybuilder.performance.rendering.GpuStageTimer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in structured runtime proof logger.
 *
 * Enable with -Dlazybuilder.performance.proof=true. It samples the live client every 120 rendered
 * frames so benchmark runs can compare frame stability and renderer ownership without adding a
 * permanent UI, background database, or always-on diagnostics cost.
 */
final class PerformanceRuntimeProofLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("LazyBuilder/Performance/Proof");
    private static final int SAMPLE_INTERVAL_FRAMES = 120;

    private final boolean enabled = Boolean.getBoolean("lazybuilder.performance.proof");
    private final FrameProofHistogram proofHistogram = new FrameProofHistogram();
    private Object worldIdentity;
    private int framesUntilSample = SAMPLE_INTERVAL_FRAMES;
    private long sample;

    void record(MinecraftClient client, FrameMonitor frameMonitor) {
        if (!enabled || client == null || client.world == null || frameMonitor == null) return;

        if (worldIdentity != client.world) {
            worldIdentity = client.world;
            framesUntilSample = SAMPLE_INTERVAL_FRAMES;
            sample = 0L;
            proofHistogram.reset();
            LOGGER.info("LB_PERF_PROOF_BEGIN interval_frames={}", SAMPLE_INTERVAL_FRAMES);
        }

        proofHistogram.record(frameMonitor.currentFrameTimeMs());

        if (--framesUntilSample > 0) return;
        framesUntilSample = SAMPLE_INTERVAL_FRAMES;

        PerformanceSnapshot snapshot = PerformanceSnapshotReader.capture(client, frameMonitor);
        if (snapshot == null) return;

        RendererCompatibility.Snapshot renderer = RendererCompatibility.detect();
        FabricLoader loader = FabricLoader.getInstance();
        OptimizationCompatibility.Policy ownership = OptimizationCompatibility.evaluate(
                renderer,
                loader.isModLoaded("immediatelyfast"),
                loader.isModLoaded("entityculling")
        );
        FirstPartyRendererReadiness.Snapshot readiness =
                FirstPartyRendererReadiness.evaluate(renderer, ownership);
        var shader = PerformanceManagerClient.currentShaderSnapshot();
        var shaderDiagnostics = PerformanceManagerClient.currentShaderDiagnostics();
        var cullingDiagnostics = PerformanceManagerClient.currentCullingDiagnostics();
        FrameProofHistogram.Snapshot timing = proofHistogram.snapshot();
        StageTimingMetrics.Snapshot entityCullTiming =
                StageTimingMetrics.snapshot(StageTimingMetrics.Stage.ENTITY_CULLING);
        StageTimingMetrics.Snapshot blockEntityCullTiming =
                StageTimingMetrics.snapshot(StageTimingMetrics.Stage.BLOCK_ENTITY_CULLING);
        StageTimingMetrics.Snapshot chunkUploadTiming =
                StageTimingMetrics.snapshot(StageTimingMetrics.Stage.CHUNK_UPLOAD);
        StageTimingMetrics.Snapshot terrainSubmissionTiming =
                StageTimingMetrics.snapshot(StageTimingMetrics.Stage.TERRAIN_SUBMISSION);
        PerformanceGovernor.Profile governor = PerformanceManagerClient.governorProfile();
        GpuCapabilityProfile.Snapshot gpu = GpuCapabilityProfile.current();
        GpuStageTimer.Snapshot terrainGpu = GpuStageTimer.snapshot(GpuStageTimer.Stage.TERRAIN);
        GpuStageTimer.Snapshot shadowGpu = GpuStageTimer.snapshot(GpuStageTimer.Stage.SHADOW);
        GpuStageTimer.Snapshot postGpu = GpuStageTimer.snapshot(GpuStageTimer.Stage.POST_PROCESS);
        String shaderStage = shader.get("stage") instanceof String value ? value : "unknown";
        boolean shaderReady = shader.get("renderingReady") instanceof Boolean value && value;
        boolean terrainIntegrated = shader.get("terrainIntegrated") instanceof Boolean value && value;
        long shadowReusedFrames = longValue(shaderDiagnostics, "shadowReusedFrames");
        int shadowResolution = intValue(shaderDiagnostics, "shadowResolution");
        long gbufferStaleRecoveries = longValue(shaderDiagnostics, "gbufferStaleFrameRecoveries");
        long shaderCompileGeneration = longValue(shaderDiagnostics, "compileGeneration");
        long shaderReloadRequests = longValue(shaderDiagnostics, "shaderReloadRequests");
        long shaderReloadFailures = longValue(shaderDiagnostics, "shaderReloadFailures");
        long invalidShaderPacks = longValue(shaderDiagnostics, "invalidPackCount");
        boolean terrainReloadPending =
                shaderDiagnostics.get("terrainReloadPending") instanceof Boolean value && value;
        long entityCacheHits = longValue(cullingDiagnostics, "entityCacheHits");
        long entityCacheStales = longValue(cullingDiagnostics, "entityCacheStales");
        long blockEntityCacheHits = longValue(cullingDiagnostics, "blockEntityCacheHits");
        long blockEntityCacheStales = longValue(cullingDiagnostics, "blockEntityCacheStales");
        long entityQueueDrops = longValue(cullingDiagnostics, "entityQueueDrops");
        long blockEntityQueueDrops = longValue(cullingDiagnostics, "blockEntityQueueDrops");

        sample++;
        LOGGER.info(
                "LB_PERF_PROOF sample={} fps={} avg_ms={} worst_ms={} pressure={} chunks_upload={} "
                        + "upload_budget_stops={} vanilla_gpu_bytes={} physical_arena_bytes={} "
                        + "physical_draws={} exclusive_buffers={} retired_bytes={} promotions={} "
                        + "recoveries={} recovery_failures={} relocations={} relocation_fallbacks={} "
                        + "rebuild_deferrals={} rebuild_releases={} terrain_buffer_cache_hits={} "
                        + "multidraw_batches={} multidraw_commands={} multidraw_failures={} renderer={} "
                        + "standalone_renderer_ready={} renderer_readiness={} "
                        + "shader_stage={} shader_ready={} shader_terrain={} "
                        + "shadow_reused_frames={} shadow_resolution={} "
                        + "gbuffer_stale_recoveries={} shader_compile_generation={} "
                        + "shader_reload_requests={} shader_reload_failures={} "
                        + "invalid_shader_packs={} terrain_reload_pending={} "
                        + "entity_cull_cache_hit={} entity_cull_cache_stale={} "
                        + "block_entity_cull_cache_hit={} block_entity_cull_cache_stale={} "
                        + "entity_cull_queue_drop={} block_entity_cull_queue_drop={} "
                        + "frame_p50_ms={} frame_p95_ms={} frame_p99_ms={} frame_p999_ms={} "
                        + "frame_proof_samples={} frame_proof_max_ms={} "
                        + "stutter_16ms={} stutter_25ms={} stutter_33ms={} stutter_50ms={} "
                        + "entity_cull_cpu_avg_ms={} entity_cull_cpu_max_ms={} "
                        + "block_entity_cull_cpu_avg_ms={} block_entity_cull_cpu_max_ms={} "
                        + "chunk_upload_cpu_avg_ms={} chunk_upload_cpu_max_ms={} "
                        + "terrain_submit_cpu_avg_ms={} terrain_submit_cpu_max_ms={} "
                        + "governor_mode={} governor_upload_budget={} governor_rebuild_budget={} "
                        + "governor_culling_budget={} governor_shadow_reuse={} "
                        + "gpu_tier={} gpu_timer_queries={} gpu_draw_id={} gpu_vram_bytes={} "
                        + "gpu_terrain_avg_ms={} gpu_terrain_max_ms={} "
                        + "gpu_shadow_avg_ms={} gpu_shadow_max_ms={} "
                        + "gpu_post_avg_ms={} gpu_post_max_ms={}",
                sample,
                snapshot.fps(),
                snapshot.averageFrameTimeMs(),
                snapshot.worstRecentFrameTimeMs(),
                snapshot.pressure(),
                snapshot.chunksToUpload(),
                snapshot.chunkUploadBudgetStops(),
                snapshot.terrainGpuResidentBytes(),
                snapshot.terrainPhysicalArenaResidentBytes(),
                snapshot.terrainPhysicalDraws(),
                snapshot.terrainExclusiveResidentBuffers(),
                snapshot.terrainExclusiveRetiredBytes(),
                snapshot.terrainExclusivePromotions(),
                snapshot.terrainExclusiveRecoveries(),
                snapshot.terrainExclusiveRecoveryFailures(),
                snapshot.terrainPhysicalRelocations(),
                snapshot.terrainPhysicalRelocationFallbacks(),
                ChunkPipelineMetrics.rebuildBackpressureDeferrals(),
                ChunkPipelineMetrics.rebuildBackpressureReleases(),
                ChunkPipelineMetrics.terrainBufferLookupHits(),
                snapshot.terrainMultiDrawSubmittedBatches(),
                snapshot.terrainMultiDrawSubmittedCommands(),
                snapshot.terrainMultiDrawSubmissionFailures(),
                snapshot.rendererPipelineOwner(),
                readiness.ready(),
                readiness.status(),
                shaderStage,
                shaderReady,
                terrainIntegrated,
                shadowReusedFrames,
                shadowResolution,
                gbufferStaleRecoveries,
                shaderCompileGeneration,
                shaderReloadRequests,
                shaderReloadFailures,
                invalidShaderPacks,
                terrainReloadPending,
                entityCacheHits,
                entityCacheStales,
                blockEntityCacheHits,
                blockEntityCacheStales,
                entityQueueDrops,
                blockEntityQueueDrops,
                timing.p50Ms(),
                timing.p95Ms(),
                timing.p99Ms(),
                timing.p999Ms(),
                timing.samples(),
                timing.maxMs(),
                timing.framesOver16_67Ms(),
                timing.framesOver25Ms(),
                timing.framesOver33_33Ms(),
                timing.framesOver50Ms(),
                entityCullTiming.averageMs(),
                entityCullTiming.maxMs(),
                blockEntityCullTiming.averageMs(),
                blockEntityCullTiming.maxMs(),
                chunkUploadTiming.averageMs(),
                chunkUploadTiming.maxMs(),
                terrainSubmissionTiming.averageMs(),
                terrainSubmissionTiming.maxMs(),
                governor.mode(),
                governor.chunkUploadBudget(),
                governor.rebuildReleaseBudget(),
                governor.cullingBudgetPercent(),
                governor.shadowReuseMultiplier(),
                gpu.tier(),
                gpu.timerQueries(),
                gpu.drawId(),
                gpu.reportedVramBytes(),
                terrainGpu.averageMs(),
                terrainGpu.maxMs(),
                shadowGpu.averageMs(),
                shadowGpu.maxMs(),
                postGpu.averageMs(),
                postGpu.maxMs()
        );
    }

    private static long longValue(java.util.Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static int intValue(java.util.Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }
}
