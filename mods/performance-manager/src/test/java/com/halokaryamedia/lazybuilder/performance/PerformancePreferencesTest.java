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
        assertTrue(defaults.entityCulling());
        assertTrue(defaults.blockEntityCulling());
        assertTrue(defaults.renderingOptimizations());
        assertEquals(30, defaults.unfocusedFpsLimit());
        assertEquals(10, defaults.minimizedFpsLimit());
    }

    @Test
    void renderingPolicyPersistsWithoutChangingOtherCapabilities() {
        PerformanceConfigStore store = new PerformanceConfigStore(tempDir);
        PerformancePreferences expected = new PerformancePreferences(
                false,
                45,
                12,
                false,
                true,
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
    }
}
