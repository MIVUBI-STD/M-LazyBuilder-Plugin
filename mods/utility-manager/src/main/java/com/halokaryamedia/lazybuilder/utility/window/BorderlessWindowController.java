package com.halokaryamedia.lazybuilder.utility.window;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

/**
 * Applies an opt-in borderless window mode once after the Minecraft client starts.
 *
 * This does not own FPS throttling or background-resource policy; those remain
 * Performance Manager responsibilities.
 */
public final class BorderlessWindowController {
    private BorderlessWindowController() {
    }

    public static void applyIfEnabled(MinecraftClient client, boolean enabled) {
        if (!enabled) return;

        Window window = client.getWindow();
        if (window == null || window.isFullscreen()) return;

        long handle = window.getHandle();
        long monitor = findBestMonitor(handle);
        if (monitor == 0L) return;

        GLFWVidMode videoMode = GLFW.glfwGetVideoMode(monitor);
        if (videoMode == null) return;

        int[] monitorX = new int[1];
        int[] monitorY = new int[1];
        GLFW.glfwGetMonitorPos(monitor, monitorX, monitorY);

        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_AUTO_ICONIFY, GLFW.GLFW_FALSE);
        GLFW.glfwSetWindowMonitor(
                handle,
                0L,
                monitorX[0],
                monitorY[0],
                videoMode.width(),
                videoMode.height(),
                GLFW.GLFW_DONT_CARE
        );
    }

    private static long findBestMonitor(long window) {
        PointerBuffer monitors = GLFW.glfwGetMonitors();
        if (monitors == null || monitors.limit() == 0) {
            return GLFW.glfwGetPrimaryMonitor();
        }

        int[] windowX = new int[1];
        int[] windowY = new int[1];
        int[] windowWidth = new int[1];
        int[] windowHeight = new int[1];
        GLFW.glfwGetWindowPos(window, windowX, windowY);
        GLFW.glfwGetWindowSize(window, windowWidth, windowHeight);

        long bestMonitor = GLFW.glfwGetPrimaryMonitor();
        long bestOverlap = -1L;

        for (int index = 0; index < monitors.limit(); index++) {
            long monitor = monitors.get(index);
            GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
            if (mode == null) continue;

            int[] monitorX = new int[1];
            int[] monitorY = new int[1];
            GLFW.glfwGetMonitorPos(monitor, monitorX, monitorY);

            int overlapWidth = Math.max(
                    0,
                    Math.min(windowX[0] + windowWidth[0], monitorX[0] + mode.width())
                            - Math.max(windowX[0], monitorX[0])
            );
            int overlapHeight = Math.max(
                    0,
                    Math.min(windowY[0] + windowHeight[0], monitorY[0] + mode.height())
                            - Math.max(windowY[0], monitorY[0])
            );
            long overlap = (long) overlapWidth * overlapHeight;

            if (overlap > bestOverlap) {
                bestOverlap = overlap;
                bestMonitor = monitor;
            }
        }

        return bestMonitor;
    }
}
