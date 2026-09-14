package com.halokaryamedia.lazybuilder.performance;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

/** Fabric client entrypoint for LazyBuilder Performance Manager. */
public final class PerformanceManagerClient implements ClientModInitializer {
    private static final BackgroundFpsController BACKGROUND_FPS = new BackgroundFpsController();

    private static PerformanceCapabilities capabilities = new PerformanceCapabilities(
            false, false, false, false, false, false, false, false, false
    );
    private static PerformancePreferences preferences = PerformancePreferences.defaults();
    private static PerformanceConfigStore configStore;

    @Override
    public void onInitializeClient() {
        capabilities = PerformanceCapabilityDetector.detect();
        configStore = new PerformanceConfigStore(FabricLoader.getInstance().getConfigDir());
        preferences = configStore.load();

        // The only continuous hook in Performance Manager is the lightweight background
        // framerate policy. It automatically becomes a no-op when Dynamic FPS is installed.
        ClientTickEvents.END_CLIENT_TICK.register(client ->
                BACKGROUND_FPS.update(client, preferences, capabilities)
        );
    }

    public static PerformanceCapabilities capabilities() {
        return capabilities;
    }

    public static PerformancePreferences preferences() {
        return preferences;
    }

    public static void updatePreferences(PerformancePreferences updated) {
        preferences = updated;
        if (configStore != null) configStore.save(updated);
    }

    /** Captures current state on demand; no metrics history or background sampling is stored. */
    public static PerformanceState currentState() {
        MinecraftClient client = MinecraftClient.getInstance();
        return PerformanceStateReader.capture(client, capabilities);
    }
}
