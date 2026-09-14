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
    void missingConfigCreatesSafeDefaults() {
        UtilityConfigStore store = new UtilityConfigStore(tempDir);

        UtilityPreferences preferences = store.load();

        assertEquals(UtilityPreferences.defaults(), preferences);
        assertTrue(Files.isRegularFile(store.configFile()));
        assertTrue(preferences.extendedChatHistory());
        assertTrue(preferences.keepChatDraft());
        assertTrue(preferences.reconnectButton());
        assertFalse(preferences.borderlessWindow());
        assertFalse(preferences.contextualScreenshotNames());
    }

    @Test
    void saveAndLoadRoundTrip() {
        UtilityConfigStore store = new UtilityConfigStore(tempDir);
        UtilityPreferences expected = new UtilityPreferences(
                true,
                false,
                false,
                false,
                true
        );

        store.save(expected);

        assertEquals(expected, store.load());
    }

    @Test
    void malformedBooleanFallsBackToFeatureDefault() throws IOException {
        UtilityConfigStore store = new UtilityConfigStore(tempDir);
        Files.writeString(
                store.configFile(),
                "window.borderless=not-a-boolean\n"
                        + "chat.extended_history=not-a-boolean\n"
                        + "chat.keep_draft=FALSE\n"
                        + "connection.reconnect_button=not-a-boolean\n"
                        + "screenshots.contextual_names=TRUE\n"
        );

        UtilityPreferences preferences = store.load();

        assertFalse(preferences.borderlessWindow());
        assertTrue(preferences.extendedChatHistory());
        assertFalse(preferences.keepChatDraft());
        assertTrue(preferences.reconnectButton());
        assertTrue(preferences.contextualScreenshotNames());
    }

    @Test
    void legacyScreenshotPreferenceIsMigratedOnRead() throws IOException {
        UtilityConfigStore store = new UtilityConfigStore(tempDir);
        Files.writeString(store.configFile(), "screenshots.organize_by_project=TRUE\n");

        UtilityPreferences preferences = store.load();

        assertTrue(preferences.contextualScreenshotNames());
    }
}
