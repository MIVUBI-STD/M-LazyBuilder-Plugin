package com.halokaryamedia.lazybuilder.utility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UtilityConfigStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void missingConfigCreatesConservativeDefaults() {
        UtilityConfigStore store = new UtilityConfigStore(tempDir);

        UtilityPreferences preferences = store.load();

        assertEquals(UtilityPreferences.defaults(), preferences);
        assertTrue(Files.isRegularFile(store.configFile()));
    }

    @Test
    void saveAndLoadRoundTrip() {
        UtilityConfigStore store = new UtilityConfigStore(tempDir);
        UtilityPreferences expected = new UtilityPreferences(true, true, true, true, true);

        store.save(expected);

        assertEquals(expected, store.load());
    }

    @Test
    void malformedBooleanFallsBackWithoutEnablingFeature() throws IOException {
        UtilityConfigStore store = new UtilityConfigStore(tempDir);
        Files.writeString(
                store.configFile(),
                "window.borderless=not-a-boolean\nconnection.auto_reconnect=TRUE\n"
        );

        UtilityPreferences preferences = store.load();

        assertFalse(preferences.borderlessWindow());
        assertTrue(preferences.autoReconnect());
        assertFalse(preferences.compactInfo());
        assertFalse(preferences.chatTimestamps());
        assertFalse(preferences.organizeScreenshotsByProject());
    }
}
