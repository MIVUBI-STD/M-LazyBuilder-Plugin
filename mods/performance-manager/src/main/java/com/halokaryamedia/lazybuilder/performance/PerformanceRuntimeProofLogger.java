package com.halokaryamedia.lazybuilder.performance;

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

        sample++;
        LOGGER.info(
                "LB_PERF_PROOF sample={} fps={} avg_ms={} worst_ms={} pressure={} chunks_upload={} "
                        + "upload_budget_stops={} vanilla_gpu_bytes={} physical_arena_bytes={} "
                        + "physical_draws={} exclusive_buffers={} retired_bytes={} promotions={} "
                        + "recoveries={} recovery_failures={} relocations={} relocation_fallbacks={} "
                        + "multidraw_batches={} multidraw_commands={} multidraw_failures={} renderer={}",
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
                snapshot.terrainMultiDrawSubmittedBatches(),
                snapshot.terrainMultiDrawSubmittedCommands(),
                snapshot.terrainMultiDrawSubmissionFailures(),
                snapshot.rendererPipelineOwner()
        );
    }
}
