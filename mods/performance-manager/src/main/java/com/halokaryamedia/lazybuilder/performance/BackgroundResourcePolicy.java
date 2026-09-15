package com.halokaryamedia.lazybuilder.performance;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;

/** First-party LazyBuilder policy for unfocused/minimized client FPS limits. */
public final class BackgroundResourcePolicy {
    private int lastAppliedLimit = Integer.MIN_VALUE;

    public void update(MinecraftClient client, PerformancePreferences preferences) {
        if (client == null || client.getWindow() == null || preferences == null) return;

        int userLimit = client.options.getMaxFps().getValue();
        Window window = client.getWindow();
        int targetLimit = targetLimit(
                userLimit,
                preferences,
                client.isWindowFocused(),
                window.isMinimized()
        );

        if (targetLimit != lastAppliedLimit) {
            client.getInactivityFpsLimiter().setMaxFps(targetLimit);
            lastAppliedLimit = targetLimit;
        }
    }

    static int targetLimit(
            int userLimit,
            PerformancePreferences preferences,
            boolean focused,
            boolean minimized
    ) {
        int safeUserLimit = Math.max(1, userLimit);
        if (preferences == null || !preferences.backgroundFpsPolicy()) return safeUserLimit;
        if (minimized) return Math.min(safeUserLimit, preferences.minimizedFpsLimit());
        if (!focused) return Math.min(safeUserLimit, preferences.unfocusedFpsLimit());
        return safeUserLimit;
    }
}
