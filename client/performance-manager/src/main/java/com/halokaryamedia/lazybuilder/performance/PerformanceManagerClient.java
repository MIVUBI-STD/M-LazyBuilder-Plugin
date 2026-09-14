package com.halokaryamedia.lazybuilder.performance;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;

/** Fabric client entrypoint for LazyBuilder Performance Manager. */
public final class PerformanceManagerClient implements ClientModInitializer {
    private static PerformanceCapabilities capabilities = new PerformanceCapabilities(
            false, false, false, false, false, false, false, false, false
    );

    @Override
    public void onInitializeClient() {
        capabilities = PerformanceCapabilityDetector.detect();
        // C3 remains observational: no renderer, FPS, graphics, or external-mod settings are changed.
    }

    public static PerformanceCapabilities capabilities() {
        return capabilities;
    }

    /** Captures current state on demand; no background sampler or permanent tick hook is registered. */
    public static PerformanceState currentState() {
        MinecraftClient client = MinecraftClient.getInstance();
        return PerformanceStateReader.capture(client, capabilities);
    }
}
