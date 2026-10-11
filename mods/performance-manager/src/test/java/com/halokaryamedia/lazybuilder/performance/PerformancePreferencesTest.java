package com.halokaryamedia.lazybuilder.performance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PerformancePreferencesTest {
    @TempDir
    Path tempDir;

    @Test
    void staleTemporaryFileCannotBeOverwrittenBySavingPreferences() throws Exception {
        PerformanceConfigStore store = new PerformanceConfigStore(tempDir);
        Path oldTemp = tempDir.resolve("lazybuilder-performance-manager.properties.tmp");
        Files.writeString(oldTemp, "preserve unrelated user file");

        store.save(PerformancePreferences.defaults());

        assertEquals("preserve unrelated user file", Files.readString(oldTemp));
        assertEquals(PerformancePreferences.defaults(), store.load());
    }

    @Test
    void unsafeConfigPathIsPreservedAndNotReplaced() throws Exception {
        Path config = tempDir.resolve("lazybuilder-performance-manager.properties");
        Files.createDirectories(config);
        Files.writeString(config.resolve("important.txt"), "USER DATA");
        PerformanceConfigStore store = new PerformanceConfigStore(tempDir);

        store.save(PerformancePreferences.defaults());

        assertEquals(PerformancePreferences.defaults(), store.load());
        assertEquals("USER DATA", Files.readString(config.resolve("important.txt")));
    }

    @Test
    void oversizedPreferenceFileIsPreservedWithoutUnboundedParsing() throws Exception {
        Path config = tempDir.resolve("lazybuilder-performance-manager.properties");
        Files.writeString(config, "x".repeat(70_000));
        PerformanceConfigStore store = new PerformanceConfigStore(tempDir);

        assertEquals(PerformancePreferences.defaults(), store.load());
        assertEquals(70_000L, Files.size(config));
    }

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
