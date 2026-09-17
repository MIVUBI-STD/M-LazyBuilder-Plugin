package com.halokaryamedia.lazybuilder.performance;

import com.halokaryamedia.lazybuilder.performance.culling.CullingRuntime;
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
    }

    public void tick(MinecraftClient client) {
        backgroundPolicy.update(client, preferences);
        cullingRuntime.tick(client, preferences);
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
        preferences = updated;
        configStore.save(updated);
        if (cullingDisabled) cullingRuntime.clear();
    }

    public PerformanceSnapshot snapshot() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null ? null : PerformanceSnapshotReader.capture(client, frameMonitor);
    }
}
