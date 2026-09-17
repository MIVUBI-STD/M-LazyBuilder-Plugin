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
        assertTrue(preferences.chatSearch());
        assertTrue(preferences.chatTimestamps());
        assertTrue(preferences.hideChatSigningIndicators());
        assertTrue(preferences.hideChatReportButton());
        assertTrue(preferences.suppressNarrator());
        assertTrue(preferences.reconnectButton());
        assertFalse(preferences.borderlessWindow());
        assertFalse(preferences.contextualScreenshotNames());
        assertTrue(preferences.instantCreativeSearch());
        assertTrue(preferences.compactDebugHud());
    }

    @Test
    void saveAndLoadRoundTrip() {
        UtilityConfigStore store = new UtilityConfigStore(tempDir);
        UtilityPreferences expected = new UtilityPreferences(
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                false
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
                        + "chat.search=not-a-boolean\n"
                        + "chat.timestamps=not-a-boolean\n"
                        + "chat.hide_signing_indicators=not-a-boolean\n"
                        + "chat.hide_report_button=not-a-boolean\n"
                        + "accessibility.suppress_narrator=not-a-boolean\n"
                        + "connection.reconnect_button=not-a-boolean\n"
                        + "screenshots.contextual_names=TRUE\n"
                        + "inventory.instant_creative_search=not-a-boolean\n"
                        + "hud.compact_debug=not-a-boolean\n"
        );

        UtilityPreferences preferences = store.load();

        assertFalse(preferences.borderlessWindow());
        assertTrue(preferences.extendedChatHistory());
        assertFalse(preferences.keepChatDraft());
        assertTrue(preferences.chatSearch());
        assertTrue(preferences.chatTimestamps());
        assertTrue(preferences.hideChatSigningIndicators());
        assertTrue(preferences.hideChatReportButton());
        assertTrue(preferences.suppressNarrator());
        assertTrue(preferences.reconnectButton());
        assertTrue(preferences.contextualScreenshotNames());
        assertTrue(preferences.instantCreativeSearch());
        assertTrue(preferences.compactDebugHud());
    }

    @Test
    void legacyScreenshotPreferenceIsMigratedOnRead() throws IOException {
        UtilityConfigStore store = new UtilityConfigStore(tempDir);
        Files.writeString(store.configFile(), "screenshots.organize_by_project=TRUE\n");

        UtilityPreferences preferences = store.load();

        assertTrue(preferences.contextualScreenshotNames());
    }
}
