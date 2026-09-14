package com.halokaryamedia.lazybuilder.performance;

import net.fabricmc.loader.api.FabricLoader;

/** Detects optional performance mods without importing or depending on their implementation code. */
public final class PerformanceCapabilityDetector {
    private PerformanceCapabilityDetector() {
    }

    public static PerformanceCapabilities detect() {
        FabricLoader loader = FabricLoader.getInstance();
        return new PerformanceCapabilities(
                loader.isModLoaded("sodium"),
                loader.isModLoaded("iris"),
                loader.isModLoaded("immediatelyfast"),
                loader.isModLoaded("ferritecore"),
                loader.isModLoaded("entityculling"),
                loader.isModLoaded("moreculling"),
                loader.isModLoaded("sodium-extra"),
                loader.isModLoaded("reeses-sodium-options"),
                loader.isModLoaded("dynamic_fps")
        );
    }
}
