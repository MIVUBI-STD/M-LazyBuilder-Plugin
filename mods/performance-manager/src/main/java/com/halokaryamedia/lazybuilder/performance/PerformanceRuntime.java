package com.halokaryamedia.lazybuilder.performance;

import com.halokaryamedia.lazybuilder.performance.culling.CullingRuntime;
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
    private final PerformanceRuntimeProofLogger proofLogger = new PerformanceRuntimeProofLogger();
    private final PerformanceConfigStore configStore;
    private PerformancePreferences preferences;

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
        backgroundPolicy.update(client, preferences);
        cullingRuntime.tick(client, preferences, frameMonitor.pressure());
    }

    public boolean shouldRender(Entity entity) {
        return cullingRuntime.shouldRender(entity, preferences);
    }

    public <E extends BlockEntity> boolean shouldRender(E blockEntity, BlockEntityRenderer<E> renderer) {
        return cullingRuntime.shouldRender(blockEntity, renderer, preferences);
    }

    public FramePressure pressure() {
        return frameMonitor.pressure();
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
            return;
        }

        preferences = updated;
        configStore.save(updated);
        if (cullingDisabled) cullingRuntime.clear();
    }

    public PerformanceSnapshot snapshot() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null ? null : PerformanceSnapshotReader.capture(client, frameMonitor);
    }
}
