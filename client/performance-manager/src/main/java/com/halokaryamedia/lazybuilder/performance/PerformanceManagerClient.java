package com.halokaryamedia.lazybuilder.performance;

import net.fabricmc.api.ClientModInitializer;

/** Fabric client entrypoint for LazyBuilder Performance Manager. */
public final class PerformanceManagerClient implements ClientModInitializer {
    private static PerformanceCapabilities capabilities = new PerformanceCapabilities(
            false, false, false, false, false, false, false, false, false
    );

    @Override
    public void onInitializeClient() {
        capabilities = PerformanceCapabilityDetector.detect();
        // C3 baseline only: detection does not change renderer, FPS, graphics, or mod settings.
    }

    public static PerformanceCapabilities capabilities() {
        return capabilities;
    }
}
