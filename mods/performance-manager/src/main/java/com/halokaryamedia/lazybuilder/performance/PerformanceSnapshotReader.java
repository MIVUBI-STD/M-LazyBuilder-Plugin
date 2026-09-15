package com.halokaryamedia.lazybuilder.performance;

import net.minecraft.client.MinecraftClient;
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
                chunkDebug,
                entityDebug,
                particleDebug
        );
    }
}
