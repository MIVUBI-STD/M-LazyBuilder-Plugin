package com.halokaryamedia.lazybuilder.performance;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric client entrypoint for LazyBuilder Performance Manager. */
public final class PerformanceManagerClient implements ClientModInitializer {
    private static PerformanceRuntime runtime;

    @Override
    public void onInitializeClient() {
        runtime = new PerformanceRuntime(FabricLoader.getInstance().getConfigDir());

        WorldRenderEvents.END.register(context ->
                runtime.recordFrame(System.nanoTime())
        );

        ClientTickEvents.END_CLIENT_TICK.register(runtime::tick);
    }

    public static FramePressure pressure() {
        return runtime == null ? FramePressure.NORMAL : runtime.pressure();
    }

    public static PerformancePreferences preferences() {
        return runtime == null ? PerformancePreferences.defaults() : runtime.preferences();
    }

    public static void updatePreferences(PerformancePreferences updated) {
        if (runtime != null) runtime.updatePreferences(updated);
    }

    /** Captures current diagnostics on demand; no metrics history database is maintained. */
    public static PerformanceSnapshot currentSnapshot() {
        return runtime == null ? null : runtime.snapshot();
    }
}
