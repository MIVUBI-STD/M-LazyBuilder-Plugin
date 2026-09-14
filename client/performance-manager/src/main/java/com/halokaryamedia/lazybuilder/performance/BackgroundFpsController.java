package com.halokaryamedia.lazybuilder.performance;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;

/**
 * Lightweight background framerate policy used only when Dynamic FPS is absent.
 * It changes the Window framerate limit temporarily and never rewrites the user's video option.
 */
public final class BackgroundFpsController {
    private int lastAppliedLimit = Integer.MIN_VALUE;

    public void update(
            MinecraftClient client,
            PerformancePreferences preferences,
            PerformanceCapabilities capabilities
    ) {
        if (client == null || client.getWindow() == null) return;
        if (capabilities.hasBackgroundFpsProvider()) return;

        int userLimit = client.options.getMaxFps().getValue();
        int targetLimit = userLimit;

        if (preferences.backgroundFpsPolicy()) {
            Window window = client.getWindow();
            long handle = window.getHandle();
            boolean minimized = GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;
            boolean focused = GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;

            if (minimized) {
                targetLimit = Math.min(userLimit, preferences.minimizedFpsLimit());
            } else if (!focused) {
                targetLimit = Math.min(userLimit, preferences.unfocusedFpsLimit());
            }
        }

        if (targetLimit != lastAppliedLimit) {
            client.getWindow().setFramerateLimit(targetLimit);
            lastAppliedLimit = targetLimit;
        }
    }
}
