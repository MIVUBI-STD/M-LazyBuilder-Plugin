package com.halokaryamedia.lazybuilder.performance;

import net.minecraft.client.MinecraftClient;

import java.nio.file.Path;

/** Single runtime owner for LazyBuilder Performance Manager P0 behavior. */
public final class PerformanceRuntime {
    private final FrameMonitor frameMonitor = new FrameMonitor();
    private final WorkloadBudget workloadBudget = new WorkloadBudget();
    private final BackgroundResourcePolicy backgroundPolicy = new BackgroundResourcePolicy();
    private final PerformanceConfigStore configStore;
    private PerformancePreferences preferences;

    public PerformanceRuntime(Path configDirectory) {
        this.configStore = new PerformanceConfigStore(configDirectory);
        this.preferences = configStore.load();
    }

    public void recordFrame(long nowNanos) {
        frameMonitor.recordFrame(nowNanos);
    }

    public void tick(MinecraftClient client) {
        workloadBudget.update(frameMonitor.pressure());
        backgroundPolicy.update(client, preferences);
    }

    public FramePressure pressure() {
        return frameMonitor.pressure();
    }

    public WorkloadBudget workloadBudget() {
        return workloadBudget;
    }

    public PerformancePreferences preferences() {
        return preferences;
    }

    public void updatePreferences(PerformancePreferences updated) {
        if (updated == null) return;
        preferences = updated;
        configStore.save(updated);
    }

    public PerformanceSnapshot snapshot() {
        return PerformanceSnapshotReader.capture(MinecraftClient.getInstance(), frameMonitor);
    }
}
