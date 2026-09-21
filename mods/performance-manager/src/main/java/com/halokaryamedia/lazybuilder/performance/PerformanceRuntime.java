package com.halokaryamedia.lazybuilder.performance;

import com.halokaryamedia.lazybuilder.performance.culling.CullingRuntime;
import com.halokaryamedia.lazybuilder.performance.rendering.ChunkRebuildBackpressure;
import com.halokaryamedia.lazybuilder.performance.rendering.GpuStageTimer;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainGpuResidencyTracker;
import com.halokaryamedia.lazybuilder.performance.rendering.TerrainPhysicalArenaManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.entity.Entity;

import java.nio.file.Path;

/** Single runtime owner for LazyBuilder Performance Manager behavior. */
public final class PerformanceRuntime {
    private final FrameMonitor frameMonitor = new FrameMonitor();
    private final BackgroundResourcePolicy backgroundPolicy = new BackgroundResourcePolicy();
    private final CullingRuntime cullingRuntime = new CullingRuntime();
    private final PerformanceGovernor governor = new PerformanceGovernor();
    private final PerformanceRuntimeProofLogger proofLogger = new PerformanceRuntimeProofLogger();
    private final PerformanceConfigStore configStore;
    private PerformancePreferences preferences;
    private volatile String lastPreferenceUpdateStatus = "ready";
    private Object activeWorldIdentity;

    public PerformanceRuntime(Path configDirectory) {
        this.configStore = new PerformanceConfigStore(configDirectory);
        this.preferences = configStore.load();
    }

    public void recordFrame(long nowNanos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null
                || client.world == null
                || client.getWindow() == null
                || !client.isWindowFocused()
                || client.getWindow().isMinimized()) {
            frameMonitor.pauseFrameClock();
            return;
        }

        int targetFps = Math.max(1, client.options.getMaxFps().getValue());
        frameMonitor.recordFrame(nowNanos, targetFps);
        proofLogger.record(client, frameMonitor);
    }

    public void tick(MinecraftClient client) {
        Object worldIdentity = client == null ? null : client.world;
        if (worldIdentity != activeWorldIdentity) {
            activeWorldIdentity = worldIdentity;
            frameMonitor.resetSession();
            StageTimingMetrics.resetSession();
            GpuStageTimer.resetSession();
            cullingRuntime.clear();
            governor.reset();
        }

        backgroundPolicy.update(client, preferences);

        PerformanceGovernor.Profile governorProfile = updateGovernor(client);

        if (preferences.entityCulling() || preferences.blockEntityCulling()) {
            cullingRuntime.tick(client, preferences, frameMonitor.pressure(), governorProfile);
        }
        if (client != null && client.worldRenderer != null && preferences.renderingOptimizations()) {
            int releaseBudget = Math.min(
                    governorProfile.rebuildReleaseBudget(),
                    com.halokaryamedia.lazybuilder.performance.rendering.ChunkRebuildBackpressurePolicy
                            .releaseBudget(frameMonitor.pressure())
            );
            ChunkRebuildBackpressure.drain(
                    client.worldRenderer.getChunkBuilder(),
                    releaseBudget
            );
        }
    }

    public boolean shouldRender(Entity entity) {
        return cullingRuntime.shouldRender(entity, preferences, frameMonitor.currentFrameNanos());
    }

    public <E extends BlockEntity> boolean shouldRender(E blockEntity, BlockEntityRenderer<E> renderer) {
        return cullingRuntime.shouldRender(
                blockEntity,
                renderer,
                preferences,
                frameMonitor.currentFrameNanos()
        );
    }

    private PerformanceGovernor.Profile updateGovernor(MinecraftClient client) {
        int uploadBacklog = 0;
        int buildBacklog = 0;
        int freeBuffers = 0;
        if (client != null && client.worldRenderer != null) {
            var builder = client.worldRenderer.getChunkBuilder();
            if (builder != null) {
                uploadBacklog = Math.max(0, builder.getChunksToUpload());
                buildBacklog = Math.max(0, builder.getToBatchCount());
                freeBuffers = Math.max(0, builder.getFreeBufferCount());
            }
        }

        Runtime jvm = Runtime.getRuntime();
        long used = jvm.totalMemory() - jvm.freeMemory();
        long max = jvm.maxMemory();
        double memoryRatio = max <= 0L ? 0.0D : Math.min(1.0D, used / (double) max);

        CullingRuntime.Snapshot culling = cullingRuntime.snapshot();
        StageTimingMetrics.Snapshot entityTiming =
                StageTimingMetrics.snapshot(StageTimingMetrics.Stage.ENTITY_CULLING);
        StageTimingMetrics.Snapshot blockTiming =
                StageTimingMetrics.snapshot(StageTimingMetrics.Stage.BLOCK_ENTITY_CULLING);
        long cacheHits = culling.entityCacheHits() + culling.blockEntityCacheHits();
        long occluded = culling.entityOccludedDecisions() + culling.blockEntityOccludedDecisions();
        double cullingCpu = Math.max(
                culling.sampledAverageEvaluationMs(),
                Math.max(entityTiming.averageMs(), blockTiming.averageMs())
        );

        return governor.update(new PerformanceGovernor.Input(
                frameMonitor.pressure(),
                frameMonitor.averageFrameTimeMs(),
                uploadBacklog,
                buildBacklog,
                freeBuffers,
                memoryRatio,
                cacheHits,
                occluded,
                cullingCpu
        ));
    }

    public FramePressure pressure() {
        return frameMonitor.pressure();
    }

    public PerformanceGovernor.Profile governorProfile() {
        return governor.profile();
    }

    public CullingRuntime.Snapshot cullingSnapshot() {
        return cullingRuntime.snapshot();
    }

    public PerformancePreferences preferences() {
        return preferences;
    }

    public void updatePreferences(PerformancePreferences updated) {
        if (updated == null) return;
        boolean cullingDisabled = (preferences.entityCulling() && !updated.entityCulling())
                || (preferences.blockEntityCulling() && !updated.blockEntityCulling());
        boolean renderingDisabled = preferences.renderingOptimizations() && !updated.renderingOptimizations();
        if (renderingDisabled && !TerrainPhysicalArenaManager.recoverAllExclusive()) {
            lastPreferenceUpdateStatus = "rendering-disable-blocked:terrain-recovery-failed";
            return;
        }
        if (renderingDisabled) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.worldRenderer != null) {
                ChunkRebuildBackpressure.releaseAll(client.worldRenderer.getChunkBuilder());
            }
            // Exclusive residents were recovered above; mirrored arena/residency state
            // is now redundant and must not retain GPU/cache resources while disabled.
            TerrainGpuResidencyTracker.clear();
        }

        preferences = updated;
        configStore.save(updated);
        lastPreferenceUpdateStatus = "applied";
        if (cullingDisabled) cullingRuntime.clear();
    }

    public String lastPreferenceUpdateStatus() {
        return lastPreferenceUpdateStatus;
    }

    public PerformanceSnapshot snapshot() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null ? null : PerformanceSnapshotReader.capture(client, frameMonitor);
    }
}
