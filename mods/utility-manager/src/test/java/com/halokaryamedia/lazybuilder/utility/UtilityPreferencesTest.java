package com.halokaryamedia.lazybuilder.utility;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UtilityPreferencesTest {
    @Test
    void screenshotNamingUpdatePreservesEveryOtherPreference() {
        UtilityPreferences original = UtilityPreferences.defaults();

        UtilityPreferences updated = original.withContextualScreenshotNames(true);

        assertTrue(updated.contextualScreenshotNames());
        assertFalse(original.contextualScreenshotNames());
        assertEquals(original.borderlessWindow(), updated.borderlessWindow());
        assertEquals(original.extendedChatHistory(), updated.extendedChatHistory());
        assertEquals(original.keepChatDraft(), updated.keepChatDraft());
        assertEquals(original.chatSearch(), updated.chatSearch());
        assertEquals(original.chatTimestamps(), updated.chatTimestamps());
        assertEquals(original.hideChatSigningIndicators(), updated.hideChatSigningIndicators());
        assertEquals(original.hideChatReportButton(), updated.hideChatReportButton());
        assertEquals(original.suppressNarrator(), updated.suppressNarrator());
        assertEquals(original.reconnectButton(), updated.reconnectButton());
        assertEquals(original.instantCreativeSearch(), updated.instantCreativeSearch());
        assertEquals(original.compactDebugHud(), updated.compactDebugHud());
    }
}
