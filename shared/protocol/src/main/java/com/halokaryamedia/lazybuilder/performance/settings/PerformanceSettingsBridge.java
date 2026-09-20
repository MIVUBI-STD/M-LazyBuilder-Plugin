package com.halokaryamedia.lazybuilder.performance.settings;

/**
 * Small shared contract between the unified Settings shell and Performance Manager.
 *
 * Performance Manager remains the runtime/config owner. Other client modules only receive
 * a stable snapshot/update surface and never depend on its implementation classes.
 */
public interface PerformanceSettingsBridge {
    String OBJECT_SHARE_KEY = "lazybuilder-performance-manager:settings-bridge";

    Snapshot snapshot();

    Snapshot defaults();

    void update(Snapshot updated);

    String lastStatus();

    record Snapshot(
            boolean backgroundFpsPolicy,
            int unfocusedFpsLimit,
            int minimizedFpsLimit,
            boolean hiddenObjectSkipping,
            boolean renderingOptimizations,
            boolean memoryOptimizations
    ) {
        public Snapshot {
            unfocusedFpsLimit = sanitizeLimit(unfocusedFpsLimit, 30);
            minimizedFpsLimit = sanitizeLimit(minimizedFpsLimit, 10);
        }

        public Snapshot withBackgroundFpsPolicy(boolean enabled) {
            return new Snapshot(
                    enabled,
                    unfocusedFpsLimit,
                    minimizedFpsLimit,
                    hiddenObjectSkipping,
                    renderingOptimizations,
                    memoryOptimizations
            );
        }

        public Snapshot withUnfocusedFpsLimit(int fps) {
            return new Snapshot(
                    backgroundFpsPolicy,
                    fps,
                    minimizedFpsLimit,
                    hiddenObjectSkipping,
                    renderingOptimizations,
                    memoryOptimizations
            );
        }

        public Snapshot withMinimizedFpsLimit(int fps) {
            return new Snapshot(
                    backgroundFpsPolicy,
                    unfocusedFpsLimit,
                    fps,
                    hiddenObjectSkipping,
                    renderingOptimizations,
                    memoryOptimizations
            );
        }

        public Snapshot withHiddenObjectSkipping(boolean enabled) {
            return new Snapshot(
                    backgroundFpsPolicy,
                    unfocusedFpsLimit,
                    minimizedFpsLimit,
                    enabled,
                    renderingOptimizations,
                    memoryOptimizations
            );
        }

        public Snapshot withRenderingOptimizations(boolean enabled) {
            return new Snapshot(
                    backgroundFpsPolicy,
                    unfocusedFpsLimit,
                    minimizedFpsLimit,
                    hiddenObjectSkipping,
                    enabled,
                    memoryOptimizations
            );
        }

        public Snapshot withMemoryOptimizations(boolean enabled) {
            return new Snapshot(
                    backgroundFpsPolicy,
                    unfocusedFpsLimit,
                    minimizedFpsLimit,
                    hiddenObjectSkipping,
                    renderingOptimizations,
                    enabled
            );
        }

        private static int sanitizeLimit(int value, int fallback) {
            return value >= 5 && value <= 260 ? value : fallback;
        }
    }
}
