package com.halokaryamedia.lazybuilder.performance;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;

/** First-party LazyBuilder policy for unfocused/minimized client FPS limits. */
public final class BackgroundResourcePolicy {
    private int lastAppliedLimit = Integer.MIN_VALUE;

    public void update(MinecraftClient client, PerformancePreferences preferences) {
        if (client == null || client.getWindow() == null) return;

        int userLimit = client.options.getMaxFps().getValue();
        int targetLimit = userLimit;

        if (preferences.backgroundFpsPolicy()) {
            Window window = client.getWindow();
            if (window.isMinimized()) {
                targetLimit = Math.min(userLimit, preferences.minimizedFpsLimit());
            } else if (!client.isWindowFocused()) {
                targetLimit = Math.min(userLimit, preferences.unfocusedFpsLimit());
            }
        }

        if (targetLimit != lastAppliedLimit) {
            client.getInactivityFpsLimiter().setMaxFps(targetLimit);
            lastAppliedLimit = targetLimit;
        }
    }
}
