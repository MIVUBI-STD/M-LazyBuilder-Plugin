package com.halokaryamedia.lazybuilder.performance;

/** Snapshot of optional performance/graphics capabilities detected in the current client. */
public record PerformanceCapabilities(
        boolean sodium,
        boolean iris,
        boolean immediatelyFast,
        boolean ferriteCore,
        boolean entityCulling,
        boolean moreCulling,
        boolean sodiumExtra,
        boolean reesesSodiumOptions,
        boolean dynamicFps
) {
    public boolean hasRendererFoundation() {
        return sodium;
    }

    public boolean hasShaderFoundation() {
        return iris;
    }

    public boolean hasBackgroundFpsProvider() {
        return dynamicFps;
    }
}
