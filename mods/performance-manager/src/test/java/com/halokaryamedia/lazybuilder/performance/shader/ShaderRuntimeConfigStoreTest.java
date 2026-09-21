package com.halokaryamedia.lazybuilder.performance.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderRuntimeConfigStoreTest {
    @TempDir Path temp;

    @Test
    void roundTripsSelectionEnabledStateAndPackOptions() {
        ShaderRuntimeConfigStore store = new ShaderRuntimeConfigStore(temp);
        ShaderRuntimePreferences expected = ShaderRuntimePreferences.defaults()
                .withSelectedPack("studio-pack")
                .withOption("studio-pack", "exposure", "1.25")
                .withOption("studio-pack", "shadows", "false")
                .withEnabled(true);

        store.save(expected);
        ShaderRuntimePreferences loaded = store.load();

        assertEquals("studio-pack", loaded.selectedPackId());
        assertTrue(loaded.enabled());
        assertEquals("1.25", loaded.optionValue("studio-pack", "exposure", ""));
        assertEquals("false", loaded.optionValue("studio-pack", "shadows", ""));
    }

    @Test
    void changingSelectionPreservesOtherPackOptions() {
        ShaderRuntimePreferences preferences = ShaderRuntimePreferences.defaults()
                .withOption("pack-a", "quality", "2")
                .withSelectedPack("pack-a")
                .withEnabled(true);

        ShaderRuntimePreferences changed = preferences.withSelectedPack("pack-b");

        assertEquals("pack-b", changed.selectedPackId());
        assertFalse(changed.enabled());
        assertEquals("2", changed.optionValue("pack-a", "quality", ""));
    }
}
