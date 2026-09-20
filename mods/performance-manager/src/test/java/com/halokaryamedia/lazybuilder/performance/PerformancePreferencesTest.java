package com.halokaryamedia.lazybuilder.performance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PerformancePreferencesTest {
    @TempDir
    Path tempDir;

    @Test
    void productionOptimizationsDefaultEnabled() {
        PerformancePreferences defaults = PerformancePreferences.defaults();

        assertTrue(defaults.backgroundFpsPolicy());
        assertFalse(defaults.entityCulling());
        assertFalse(defaults.blockEntityCulling());
        assertTrue(defaults.renderingOptimizations());
        assertTrue(defaults.memoryOptimizations());
        assertEquals(30, defaults.unfocusedFpsLimit());
        assertEquals(10, defaults.minimizedFpsLimit());
    }

    @Test
    void userFacingHiddenObjectSettingKeepsInternalCullingPathsTogether() {
        PerformancePreferences defaults = PerformancePreferences.defaults();

        PerformancePreferences enabled = defaults.withHiddenObjectSkipping(true);
        assertTrue(enabled.hiddenObjectSkipping());
        assertTrue(enabled.entityCulling());
        assertTrue(enabled.blockEntityCulling());
        assertEquals(defaults.unfocusedFpsLimit(), enabled.unfocusedFpsLimit());
        assertEquals(defaults.minimizedFpsLimit(), enabled.minimizedFpsLimit());
        assertEquals(defaults.renderingOptimizations(), enabled.renderingOptimizations());
        assertEquals(defaults.memoryOptimizations(), enabled.memoryOptimizations());

        PerformancePreferences disabled = enabled.withHiddenObjectSkipping(false);
        assertFalse(disabled.hiddenObjectSkipping());
        assertFalse(disabled.entityCulling());
        assertFalse(disabled.blockEntityCulling());
    }

    @Test
    void performancePoliciesPersistWithoutCrossChangingCapabilities() {
        PerformanceConfigStore store = new PerformanceConfigStore(tempDir);
        PerformancePreferences expected = new PerformancePreferences(
                false,
                45,
                12,
                false,
                true,
                false,
                false
        );

        store.save(expected);
        PerformancePreferences loaded = store.load();

        assertFalse(loaded.backgroundFpsPolicy());
        assertEquals(45, loaded.unfocusedFpsLimit());
        assertEquals(12, loaded.minimizedFpsLimit());
        assertFalse(loaded.entityCulling());
        assertTrue(loaded.blockEntityCulling());
        assertFalse(loaded.renderingOptimizations());
        assertFalse(loaded.memoryOptimizations());
    }
}
