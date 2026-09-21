package com.halokaryamedia.lazybuilder.performance;

import com.halokaryamedia.lazybuilder.performance.compatibility.FirstPartyRendererReadiness;
import com.halokaryamedia.lazybuilder.performance.compatibility.OptimizationCompatibility;
import com.halokaryamedia.lazybuilder.performance.compatibility.RendererCompatibility;
import com.halokaryamedia.lazybuilder.performance.rendering.ChunkPipelineMetrics;
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
    private Object worldIdentity;
    private int framesUntilSample = SAMPLE_INTERVAL_FRAMES;
    private long sample;

    void record(MinecraftClient client, FrameMonitor frameMonitor) {
        if (!enabled || client == null || client.world == null || frameMonitor == null) return;

        if (worldIdentity != client.world) {
            worldIdentity = client.world;
            framesUntilSample = SAMPLE_INTERVAL_FRAMES;
            sample = 0L;
            LOGGER.info("LB_PERF_PROOF_BEGIN interval_frames={}", SAMPLE_INTERVAL_FRAMES);
        }

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
        String shaderStage = shader.get("stage") instanceof String value ? value : "unknown";
        boolean shaderReady = shader.get("renderingReady") instanceof Boolean value && value;
        boolean terrainIntegrated = shader.get("terrainIntegrated") instanceof Boolean value && value;
        long shadowReusedFrames = longValue(shaderDiagnostics, "shadowReusedFrames");
        int shadowResolution = intValue(shaderDiagnostics, "shadowResolution");
        long gbufferStaleRecoveries = longValue(shaderDiagnostics, "gbufferStaleFrameRecoveries");
        long shaderCompileGeneration = longValue(shaderDiagnostics, "compileGeneration");
        boolean terrainReloadPending =
                shaderDiagnostics.get("terrainReloadPending") instanceof Boolean value && value;

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
                        + "terrain_reload_pending={}",
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
                terrainReloadPending
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
