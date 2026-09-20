package com.halokaryamedia.lazybuilder.performance.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PerformanceSettingsBridgeTest {
    @Test
    void snapshotUpdatesOneUserFacingSettingAtATime() {
        PerformanceSettingsBridge.Snapshot base =
                new PerformanceSettingsBridge.Snapshot(true, 30, 10, false, true, true);

        PerformanceSettingsBridge.Snapshot background = base.withUnfocusedFpsLimit(60);
        assertEquals(60, background.unfocusedFpsLimit());
        assertEquals(10, background.minimizedFpsLimit());
        assertFalse(background.hiddenObjectSkipping());
        assertTrue(background.renderingOptimizations());
        assertTrue(background.memoryOptimizations());

        PerformanceSettingsBridge.Snapshot hidden = background.withHiddenObjectSkipping(true);
        assertTrue(hidden.hiddenObjectSkipping());
        assertEquals(60, hidden.unfocusedFpsLimit());

        PerformanceSettingsBridge.Snapshot rendering = hidden.withRenderingOptimizations(false);
        assertFalse(rendering.renderingOptimizations());
        assertTrue(rendering.memoryOptimizations());
    }

    @Test
    void snapshotSanitizesInvalidFrameLimits() {
        PerformanceSettingsBridge.Snapshot snapshot =
                new PerformanceSettingsBridge.Snapshot(true, 1, 999, false, true, true);

        assertEquals(30, snapshot.unfocusedFpsLimit());
        assertEquals(10, snapshot.minimizedFpsLimit());
    }
}
