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
    @Test
    void narratorSuppressionUpdatePreservesEveryOtherPreference() {
        UtilityPreferences original = UtilityPreferences.defaults();

        UtilityPreferences updated = original.withSuppressNarrator(false);

        assertFalse(updated.suppressNarrator());
        assertTrue(original.suppressNarrator());
        assertEquals(original.borderlessWindow(), updated.borderlessWindow());
        assertEquals(original.extendedChatHistory(), updated.extendedChatHistory());
        assertEquals(original.keepChatDraft(), updated.keepChatDraft());
        assertEquals(original.chatSearch(), updated.chatSearch());
        assertEquals(original.chatTimestamps(), updated.chatTimestamps());
        assertEquals(original.hideChatSigningIndicators(), updated.hideChatSigningIndicators());
        assertEquals(original.hideChatReportButton(), updated.hideChatReportButton());
        assertEquals(original.reconnectButton(), updated.reconnectButton());
        assertEquals(original.contextualScreenshotNames(), updated.contextualScreenshotNames());
        assertEquals(original.instantCreativeSearch(), updated.instantCreativeSearch());
        assertEquals(original.compactDebugHud(), updated.compactDebugHud());
    }

    @Test
    void chatAndWindowUpdatesChangeOnlyTheirOwnedPreference() {
        UtilityPreferences original = UtilityPreferences.defaults();

        UtilityPreferences chat = original.withChatSearch(false);
        assertFalse(chat.chatSearch());
        assertEquals(original.borderlessWindow(), chat.borderlessWindow());
        assertEquals(original.keepChatDraft(), chat.keepChatDraft());
        assertEquals(original.chatTimestamps(), chat.chatTimestamps());
        assertEquals(original.suppressNarrator(), chat.suppressNarrator());

        UtilityPreferences window = original.withBorderlessWindow(true);
        assertTrue(window.borderlessWindow());
        assertEquals(original.chatSearch(), window.chatSearch());
        assertEquals(original.compactDebugHud(), window.compactDebugHud());
        assertEquals(original.reconnectButton(), window.reconnectButton());
    }

}
