package com.halokaryamedia.lazybuilder.performance;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;

/** Aggregates diagnostics only when requested; no background sampling is registered here. */
public final class PerformanceSnapshotReader {
    private PerformanceSnapshotReader() {
    }

    public static PerformanceSnapshot capture(MinecraftClient client, FrameMonitor frameMonitor) {
        int fps = Math.max(0, client.getCurrentFps());
        double currentFrameTimeMs = fps > 0 ? 1000.0D / fps : 0.0D;

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();

        int renderDistance = client.options.getViewDistance().getValue();
        int simulationDistance = client.options.getSimulationDistance().getValue();

        Window window = client.getWindow();
        boolean focused = true;
        boolean minimized = false;
        if (window != null) {
            long handle = window.getHandle();
            focused = GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
            minimized = GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;
        }

        return new PerformanceSnapshot(
                fps,
                currentFrameTimeMs,
                frameMonitor.averageFrameTimeMs(),
                frameMonitor.worstRecentFrameTimeMs(),
                usedMemory,
                maxMemory,
                renderDistance,
                simulationDistance,
                focused,
                minimized,
                frameMonitor.pressure()
        );
    }
}
