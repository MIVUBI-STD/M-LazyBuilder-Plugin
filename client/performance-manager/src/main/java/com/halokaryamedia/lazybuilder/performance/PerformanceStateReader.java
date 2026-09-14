package com.halokaryamedia.lazybuilder.performance;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;

/** Captures lightweight performance state only when requested. No polling is registered. */
public final class PerformanceStateReader {
    private PerformanceStateReader() {
    }

    public static PerformanceState capture(MinecraftClient client, PerformanceCapabilities capabilities) {
        int fps = Math.max(0, client.getCurrentFps());
        double frameTimeMs = fps > 0 ? 1000.0D / fps : 0.0D;

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

        return new PerformanceState(
                fps,
                frameTimeMs,
                usedMemory,
                maxMemory,
                renderDistance,
                simulationDistance,
                focused,
                minimized,
                capabilities
        );
    }
}
